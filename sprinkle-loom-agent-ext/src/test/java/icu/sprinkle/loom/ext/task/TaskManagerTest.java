package icu.sprinkle.loom.ext.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskManager + FileTaskStore 测试。
 *
 * @author sprinkle
 * @since 2026/4/4
 */
class TaskManagerTest {

    @TempDir
    Path tempDir;

    private TaskManager manager;

    @BeforeEach
    void setUp() {
        FileTaskStore store = new FileTaskStore(tempDir);
        manager = new TaskManager(store);
    }

    @Test
    void create_simpleTask() {
        Task task = manager.create("Refactor ToolExecutor", "Break into smaller classes", List.of());

        assertThat(task.id()).isPositive();
        assertThat(task.subject()).isEqualTo("Refactor ToolExecutor");
        assertThat(task.status()).isEqualTo(Task.STATUS_PENDING);
        assertThat(task.blockedBy()).isEmpty();
    }

    @Test
    void create_withDependency() {
        Task t1 = manager.create("Task A", "", List.of());
        Task t2 = manager.create("Task B depends on A", "", List.of(t1.id()));

        assertThat(t2.blockedBy()).containsExactly(t1.id());
        assertThat(t2.isBlocked()).isTrue();

        // Task A should have t2 in its blocks list
        Task reloaded = manager.get(t1.id()).orElseThrow();
        assertThat(reloaded.blocks()).contains(t2.id());
    }

    @Test
    void create_nonExistentDependency_throws() {
        assertThatThrownBy(() -> manager.create("Bad task", "", List.of(999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateStatus_completedClearsDependency() {
        Task t1 = manager.create("Task A", "", List.of());
        Task t2 = manager.create("Task B", "", List.of(t1.id()));

        assertThat(manager.get(t2.id()).orElseThrow().isBlocked()).isTrue();

        // Complete Task A
        manager.updateStatus(t1.id(), Task.STATUS_COMPLETED);

        // Task B should no longer be blocked
        Task t2Updated = manager.get(t2.id()).orElseThrow();
        assertThat(t2Updated.isBlocked()).isFalse();
        assertThat(t2Updated.blockedBy()).isEmpty();
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        Task t1 = manager.create("Task", "", List.of());
        assertThatThrownBy(() -> manager.updateStatus(t1.id(), "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listAll_returnsAllTasks() {
        manager.create("A", "", List.of());
        manager.create("B", "", List.of());
        manager.create("C", "", List.of());

        assertThat(manager.listAll()).hasSize(3);
    }

    @Test
    void listUnclaimed_filtersBlockedAndOwned() {
        Task t1 = manager.create("Available", "", List.of());
        Task t2 = manager.create("Blocked", "", List.of(t1.id()));

        List<Task> unclaimed = manager.listUnclaimed();
        assertThat(unclaimed).hasSize(1);
        assertThat(unclaimed.get(0).subject()).isEqualTo("Available");
    }

    @Test
    void claim_setsOwnerAndStatus() {
        Task task = manager.create("Task", "", List.of());
        Task claimed = manager.claim(task.id(), "agent-1");

        assertThat(claimed.owner()).isEqualTo("agent-1");
        assertThat(claimed.status()).isEqualTo(Task.STATUS_IN_PROGRESS);
    }

    @Test
    void claim_blockedTask_throws() {
        Task t1 = manager.create("Dep", "", List.of());
        Task t2 = manager.create("Blocked", "", List.of(t1.id()));

        assertThatThrownBy(() -> manager.claim(t2.id(), "agent"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void fileTaskStore_persistsAcrossInstances() {
        manager.create("Persistent Task", "Should survive reload", List.of());

        // Create new manager with same directory
        FileTaskStore newStore = new FileTaskStore(tempDir);
        TaskManager newManager = new TaskManager(newStore);

        assertThat(newManager.listAll()).hasSize(1);
        assertThat(newManager.listAll().get(0).subject()).isEqualTo("Persistent Task");
    }
}
