package icu.sprinkle.loom.core.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

/**
 * 基于独立 Git 仓库的文件快照实现。
 *
 * <p>在 Agent 工作目录下创建一个隐藏的 Shadow Git 仓库
 * （{@code .sprinkle-loom/snapshots/.git}），专门追踪 Agent 的文件变更，
 * 不影响项目本身的版本控制系统。</p>
 *
 * <h3>边界处理</h3>
 * <ul>
 *   <li>文件大于 {@value #MAX_FILE_SIZE} 字节时跳过快照</li>
 *   <li>二进制文件（前 8KB 含 NUL 字节）跳过快照</li>
 *   <li>Git 操作通过 {@code synchronized} 串行化，避免仓库损坏</li>
 * </ul>
 *
 * @author sprinkle
 * @since 2026/3/27
 */
public final class GitFileSnapshot implements FileSnapshot {

    private static final Logger log = LoggerFactory.getLogger(GitFileSnapshot.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int BINARY_CHECK_SIZE = 8192;

    private final Path workdir;
    private final Path snapshotDir;
    private final Deque<String> redoStack = new ArrayDeque<>();

    /**
     * 创建 GitFileSnapshot 实例。如果 Shadow Git 仓库不存在则自动初始化。
     *
     * @param workdir Agent 工作目录
     * @throws SnapshotException 若 Git 不可用或初始化失败
     */
    public GitFileSnapshot(Path workdir) {
        this.workdir = workdir.toAbsolutePath().normalize();
        this.snapshotDir = this.workdir.resolve(".sprinkle-loom").resolve("snapshots");

        checkGitAvailable();
        initRepository();
    }

    @Override
    public synchronized void snapshot(Path filePath, OperationType operation, String description) {
        Path originalPath = workdir.resolve(filePath).normalize();

        if (operation != OperationType.DELETE) {
            if (!Files.exists(originalPath)) {
                log.warn("快照跳过：文件不存在 {}", filePath);
                return;
            }
            try {
                if (Files.size(originalPath) > MAX_FILE_SIZE) {
                    log.info("快照跳过：文件过大 {} (>{} MB)", filePath, MAX_FILE_SIZE / 1024 / 1024);
                    return;
                }
                if (isBinary(originalPath)) {
                    log.info("快照跳过：二进制文件 {}", filePath);
                    return;
                }
            } catch (IOException e) {
                log.warn("快照跳过：无法读取文件 {}", filePath, e);
                return;
            }
        }

        try {
            Path snapshotPath = snapshotDir.resolve(filePath).normalize();

            switch (operation) {
                case CREATE, MODIFY -> {
                    Files.createDirectories(snapshotPath.getParent());
                    Files.copy(originalPath, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
                }
                case DELETE -> Files.deleteIfExists(snapshotPath);
            }

            runGit("add", "-A");
            runGit("commit", "-m", description, "--allow-empty");

            redoStack.clear();
            log.debug("文件快照已记录: {} [{}]", filePath, operation);
        } catch (IOException e) {
            log.warn("文件快照失败: {}", filePath, e);
        }
    }

    @Override
    public synchronized Optional<String> undo() {
        try {
            String parentCount = runGit("rev-list", "--count", "HEAD").trim();
            if ("1".equals(parentCount)) {
                return Optional.empty();
            }

            String currentCommit = runGit("rev-parse", "HEAD").trim();
            String description = runGit("log", "-1", "--format=%s").trim();

            runGit("reset", "--hard", "HEAD~1");
            syncSnapshotToWorkdir();

            redoStack.push(currentCommit);
            return Optional.of("Undone: " + description);
        } catch (Exception e) {
            log.warn("Undo 操作失败", e);
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<String> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        }

        try {
            String commitToRedo = redoStack.pop();
            String description = runGit("log", "-1", "--format=%s", commitToRedo).trim();

            runGit("reset", "--hard", commitToRedo);
            syncSnapshotToWorkdir();

            return Optional.of("Redone: " + description);
        } catch (Exception e) {
            log.warn("Redo 操作失败", e);
            return Optional.empty();
        }
    }

    @Override
    public synchronized List<ChangeRecord> history(int limit) {
        try {
            String logOutput = runGit("log", "--format=%H|%s|%aI",
                    "-" + Math.max(1, limit), "--skip=1");
            return parseLogOutput(logOutput);
        } catch (Exception e) {
            log.warn("获取历史记录失败", e);
            return List.of();
        }
    }

    @Override
    public synchronized List<ChangeRecord> fileHistory(Path filePath, int limit) {
        try {
            String logOutput = runGit("log", "--format=%H|%s|%aI",
                    "-" + Math.max(1, limit), "--", filePath.toString());
            return parseLogOutput(logOutput);
        } catch (Exception e) {
            log.warn("获取文件历史记录失败: {}", filePath, e);
            return List.of();
        }
    }

    /**
     * 检查系统 Git 是否可用。
     */
    private void checkGitAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new SnapshotException("Git 不可用（exit code: " + exitCode + "）");
            }
        } catch (IOException | InterruptedException e) {
            throw new SnapshotException("Git 不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化 Shadow Git 仓库。
     */
    private void initRepository() {
        try {
            if (Files.exists(snapshotDir.resolve(".git"))) {
                return;
            }

            Files.createDirectories(snapshotDir);
            runGit("init");
            runGit("config", "user.email", "sprinkle-loom@local");
            runGit("config", "user.name", "Sprinkle-Loom Snapshot");

            writeGitignore();

            runGit("add", "-A");
            runGit("commit", "--allow-empty", "-m", "Initial snapshot");
            log.info("Shadow Git 仓库已初始化: {}", snapshotDir);
        } catch (IOException e) {
            throw new SnapshotException("Shadow Git 仓库初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 写入 Shadow 仓库的 .gitignore。
     */
    private void writeGitignore() throws IOException {
        String gitignore = """
                *.class
                *.jar
                *.war
                *.ear
                *.zip
                *.tar.gz
                *.png
                *.jpg
                *.gif
                *.ico
                *.pdf
                *.bin
                """;
        Files.writeString(snapshotDir.resolve(".gitignore"), gitignore);
    }

    /**
     * 将 snapshot 目录中的文件同步到实际工作目录（undo/redo 后调用）。
     */
    private void syncSnapshotToWorkdir() throws IOException {
        String tracked = runGit("ls-files");
        for (String relativePath : tracked.split("\n")) {
            if (relativePath.isBlank() || ".gitignore".equals(relativePath.trim())) {
                continue;
            }

            Path snapshotFile = snapshotDir.resolve(relativePath.trim());
            Path workdirFile = workdir.resolve(relativePath.trim());

            if (Files.exists(snapshotFile)) {
                Files.createDirectories(workdirFile.getParent());
                Files.copy(snapshotFile, workdirFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(workdirFile);
            }
        }
    }

    /**
     * 在 snapshot Git 仓库中执行 Git 命令。
     */
    private String runGit(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(snapshotDir.toString());
        command.addAll(List.of(args));

        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new SnapshotException(
                        "Git command failed: " + String.join(" ", args) + "\n" + output);
            }
            return output.trim();
        } catch (IOException | InterruptedException e) {
            throw new SnapshotException(
                    "Git command error: " + String.join(" ", args), e);
        }
    }

    /**
     * 检查文件是否为二进制文件（前 8KB 含 NUL 字节）。
     */
    private boolean isBinary(Path path) throws IOException {
        byte[] buf = new byte[BINARY_CHECK_SIZE];
        try (var is = Files.newInputStream(path)) {
            int read = is.read(buf);
            for (int i = 0; i < read; i++) {
                if (buf[i] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 解析 git log 输出为 ChangeRecord 列表。
     */
    private List<ChangeRecord> parseLogOutput(String logOutput) {
        if (logOutput.isBlank()) {
            return List.of();
        }

        List<ChangeRecord> records = new ArrayList<>();
        for (String line : logOutput.split("\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }
            String commitId = parts[0].trim();
            String description = parts[1].trim();
            Instant timestamp;
            try {
                timestamp = Instant.parse(parts[2].trim());
            } catch (Exception e) {
                timestamp = Instant.now();
            }

            OperationType opType = guessOperationType(description);
            Path filePath = guessFilePath(description);
            records.add(new ChangeRecord(commitId, filePath, opType, description, timestamp));
        }
        return records;
    }

    private OperationType guessOperationType(String description) {
        String lower = description.toLowerCase();
        if (lower.contains("create") || lower.contains("新建")) {
            return OperationType.CREATE;
        }
        if (lower.contains("delete") || lower.contains("删除")) {
            return OperationType.DELETE;
        }
        return OperationType.MODIFY;
    }

    private Path guessFilePath(String description) {
        int colonIdx = description.indexOf(':');
        if (colonIdx >= 0 && colonIdx < description.length() - 1) {
            return Path.of(description.substring(colonIdx + 1).trim());
        }
        return Path.of("unknown");
    }
}
