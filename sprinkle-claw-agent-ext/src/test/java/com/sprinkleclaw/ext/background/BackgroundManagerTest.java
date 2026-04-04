package com.sprinkleclaw.ext.background;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BackgroundManager 测试。
 *
 * <p>注意：不使用 {@code @TempDir} 作为 BackgroundManager 的 workdir，
 * 因为 Windows 上子进程可能锁定目录导致 JUnit 清理失败。</p>
 *
 * @author sprinkle
 * @since 2026/4/4
 */
class BackgroundManagerTest {

    private BackgroundManager manager;

    @AfterEach
    void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
            manager = null;
        }
    }

    @Test
    void run_returnsTaskId() {
        manager = new BackgroundManager(Path.of("."), Duration.ofMinutes(1));
        String taskId = manager.run("echo hello");

        assertThat(taskId).isNotNull().isNotEmpty();
    }

    @Test
    void get_returnsTaskAfterRun() throws InterruptedException {
        manager = new BackgroundManager(Path.of("."), Duration.ofMinutes(1));
        String taskId = manager.run("echo test");

        var task = manager.get(taskId);
        assertThat(task).isPresent();

        // Wait for completion
        Thread.sleep(2000);

        task = manager.get(taskId);
        assertThat(task).isPresent();
        assertThat(task.get().status()).isIn(
                BackgroundTask.STATUS_RUNNING, BackgroundTask.STATUS_COMPLETED);
    }

    @Test
    void drainNotifications_afterCompletion() throws InterruptedException {
        manager = new BackgroundManager(Path.of("."), Duration.ofMinutes(1));
        manager.run("echo notification-test");

        Thread.sleep(2000);

        List<TaskNotification> notifications = manager.drainNotifications();
        assertThat(notifications).isNotNull();
    }

    @Test
    void cancel_runningTask() throws InterruptedException {
        manager = new BackgroundManager(Path.of("."), Duration.ofMinutes(1));
        String os = System.getProperty("os.name", "").toLowerCase();
        String cmd = os.contains("win") ? "ping -n 30 127.0.0.1" : "sleep 30";
        String taskId = manager.run(cmd);

        Thread.sleep(500);

        boolean cancelled = manager.cancel(taskId);
        assertThat(cancelled).isTrue();

        Thread.sleep(1000);

        var task = manager.get(taskId);
        assertThat(task).isPresent();
        assertThat(task.get().status()).isIn(
                BackgroundTask.STATUS_CANCELLED, BackgroundTask.STATUS_ERROR);
    }

    @Test
    void listAll_returnsAllTasks() {
        manager = new BackgroundManager(Path.of("."), Duration.ofMinutes(1));
        manager.run("echo a");
        manager.run("echo b");

        assertThat(manager.listAll()).hasSize(2);
    }
}
