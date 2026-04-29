package icu.sprinkle.loom.tool.builtin;

import icu.sprinkle.loom.protocol.tool.ToolDefinition;
import icu.sprinkle.loom.protocol.tool.ToolResult;
import icu.sprinkle.loom.tool.AgentTool;
import icu.sprinkle.loom.tool.RiskLevel;
import icu.sprinkle.loom.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行工具。
 * <p>根据操作系统自动选择 bash 或 cmd 执行命令，支持超时控制。</p>
 *
 * @author sprinkle
 * @since 2026/3/20
 */
public final class BashTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    private final Duration timeout;

    public BashTool() {
        this(DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout 命令执行超时时间
     */
    public BashTool(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "command", Map.of(
                        "type", "string",
                        "description", "The shell command to execute"
                )
        ));
        schema.put("required", List.of("command"));

        return ToolDefinition.of("bash",
                ToolDescriptions.load("bash",
                        "Execute a shell command and return stdout/stderr."),
                schema);
    }

    /**
     * 执行 Shell 命令并返回标准输出和标准错误的合并内容。
     * <p>Windows 使用 cmd /c，其他系统使用 bash -c。超时后强制终止进程。</p>
     */
    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String command = (String) input.get("command");
        if (command == null || command.isBlank()) {
            return ToolResult.error(name(), "No command provided");
        }

        Path workdir = context.workingDirectory();
        log.debug("在 {} 中执行命令: {}", workdir, command);

        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }
            pb.directory(workdir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ToolResult.error(name(),
                        "Command timed out after " + timeout.toSeconds() + "s\n" + output);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ToolResult.success(name(), "Exit code: " + exitCode + "\n" + output);
            }
            return ToolResult.success(name(), output);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.error(name(), "Error: " + e.getMessage());
        }
    }

    @Override
    public RiskLevel riskLevel() { return RiskLevel.HIGH; }
}
