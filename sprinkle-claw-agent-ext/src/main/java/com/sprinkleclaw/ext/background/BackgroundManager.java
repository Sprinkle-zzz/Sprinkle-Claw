package com.sprinkleclaw.ext.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 后台任务管理器，使用 Virtual Thread 非阻塞执行长耗时命令。
 *
 * <p>执行完成后将通知放入 {@link ConcurrentLinkedQueue}，
 * 由 {@link BackgroundNotificationHook} 在下一轮 LLM 调用前 drain 并注入上下文。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class BackgroundManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(BackgroundManager.class);

    /** 最大读取输出字节数（防止 OOM） */
    static final int MAX_OUTPUT_BYTES = 512 * 1024;
    /** 通知结果预览最大长度 */
    static final int RESULT_PREVIEW_LENGTH = 500;

    private final ConcurrentHashMap<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<TaskNotification> notificationQueue = new ConcurrentLinkedQueue<>();
    private final Path workdir;
    private final Duration taskTimeout;
    private final ExecutorService executor;

    public BackgroundManager(Path workdir, Duration taskTimeout) {
        this.workdir = workdir;
        this.taskTimeout = taskTimeout;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 启动后台任务，立即返回任务 ID（不等待执行结果）。
     *
     * @param command 要执行的 shell 命令
     * @return 任务 ID
     */
    public String run(String command) {
        String taskId = generateId();
        BackgroundTask task = new BackgroundTask(
                taskId, command, BackgroundTask.STATUS_RUNNING,
                null, null, Instant.now(), null
        );
        tasks.put(taskId, task);

        executor.submit(() -> execute(taskId, command));

        log.info("Background task {} started: {}", taskId,
                command.length() > 80 ? command.substring(0, 80) + "..." : command);
        return taskId;
    }

    private void execute(String taskId, String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(workdir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            runningProcesses.put(taskId, process);

            byte[] outputBytes;
            try (var is = process.getInputStream()) {
                outputBytes = is.readNBytes(MAX_OUTPUT_BYTES);
            }

            String output = new String(outputBytes, StandardCharsets.UTF_8);
            boolean finished = process.waitFor(taskTimeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            runningProcesses.remove(taskId);

            if (!finished) {
                process.destroyForcibly();
                completeTask(taskId, BackgroundTask.STATUS_TIMEOUT,
                        "Timeout after " + taskTimeout.toSeconds() + "s\n" + output, -1);
            } else {
                int exitCode = process.exitValue();
                String status = exitCode == 0
                        ? BackgroundTask.STATUS_COMPLETED
                        : BackgroundTask.STATUS_ERROR;
                completeTask(taskId, status, output, exitCode);
            }
        } catch (Exception e) {
            runningProcesses.remove(taskId);
            log.error("Background task {} failed", taskId, e);
            completeTask(taskId, BackgroundTask.STATUS_ERROR, e.getMessage(), -1);
        }
    }

    private void completeTask(String taskId, String status, String result, int exitCode) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null) {
            return;
        }

        BackgroundTask completed = new BackgroundTask(
                taskId, task.command(), status,
                result, exitCode, task.startedAt(), Instant.now()
        );
        tasks.put(taskId, completed);

        // 构建通知
        String preview = (result != null && result.length() > RESULT_PREVIEW_LENGTH)
                ? result.substring(0, RESULT_PREVIEW_LENGTH) + "..."
                : result;
        String cmdPreview = task.command().length() > 80
                ? task.command().substring(0, 80) + "..."
                : task.command();

        notificationQueue.add(new TaskNotification(
                taskId, status, cmdPreview, preview, exitCode, completed.elapsed()
        ));

        log.info("Background task {} {}: exit={}, elapsed={}s",
                taskId, status, exitCode, completed.elapsed().toSeconds());
    }

    /**
     * 获取指定任务的当前状态。
     *
     * @param taskId 任务 ID
     * @return 任务记录（不存在则返回空）
     */
    public Optional<BackgroundTask> get(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * 取消运行中的后台任务。
     *
     * @param taskId 任务 ID
     * @return 是否成功取消（任务不存在或已结束时返回 false）
     */
    public boolean cancel(String taskId) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null || !task.isRunning()) {
            return false;
        }

        Process process = runningProcesses.remove(taskId);
        if (process != null) {
            process.destroyForcibly();
        }

        completeTask(taskId, BackgroundTask.STATUS_CANCELLED, "Task cancelled by user.", -1);
        return true;
    }

    /**
     * 列出所有后台任务（按启动时间倒序）。
     */
    public List<BackgroundTask> listAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(BackgroundTask::startedAt).reversed())
                .toList();
    }

    /**
     * 排空通知队列，由 {@link BackgroundNotificationHook} 在 preLlmCall 时调用。
     *
     * @return 所有待处理通知（清空后队列为空）
     */
    public List<TaskNotification> drainNotifications() {
        var notifications = new ArrayList<TaskNotification>();
        TaskNotification n;
        while ((n = notificationQueue.poll()) != null) {
            notifications.add(n);
        }
        return notifications;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        // 强制结束所有运行中进程
        runningProcesses.values().forEach(Process::destroyForcibly);
        runningProcesses.clear();
        tasks.values().stream()
                .filter(BackgroundTask::isRunning)
                .forEach(t -> log.warn("Background task {} still running at shutdown", t.id()));
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
