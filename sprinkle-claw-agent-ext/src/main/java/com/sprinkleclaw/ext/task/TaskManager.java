package com.sprinkleclaw.ext.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * 持久化任务板管理器。
 *
 * <p>提供任务 CRUD 操作、依赖图管理（blockedBy/blocks 双向维护）、
 * 循环依赖检测（DFS）以及任务完成时的依赖自动清除。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final TaskStore store;

    public TaskManager(TaskStore store) {
        this.store = store;
    }

    /**
     * 创建任务，并维护依赖双向关联。
     *
     * @param subject     任务标题
     * @param description 任务描述
     * @param blockedBy   依赖的任务 ID 列表
     * @return 创建好的任务
     * @throws IllegalArgumentException 依赖任务不存在或存在循环依赖
     */
    public Task create(String subject, String description, List<Integer> blockedBy) {
        int id = store.nextId();

        // 验证 blockedBy 中的任务存在
        for (int depId : blockedBy) {
            if (store.load(depId).isEmpty()) {
                throw new IllegalArgumentException("Dependency task #" + depId + " not found");
            }
        }

        // 循环依赖检测（DFS）
        detectCycle(id, blockedBy);

        Task task = new Task(
                id, subject,
                description != null ? description : "",
                Task.STATUS_PENDING, "", "",
                List.copyOf(blockedBy), List.of(),
                Instant.now(), Instant.now()
        );
        store.save(task);

        // 更新被依赖任务的 blocks 列表
        for (int depId : blockedBy) {
            store.load(depId).ifPresent(dep -> {
                var newBlocks = new ArrayList<>(dep.blocks());
                if (!newBlocks.contains(id)) {
                    newBlocks.add(id);
                    store.save(dep.withBlocks(List.copyOf(newBlocks)));
                }
            });
        }

        log.info("Created task #{}: {}", id, subject);
        return task;
    }

    /**
     * 更新任务状态。完成（completed）时自动清除下游任务的依赖。
     *
     * @param taskId    任务 ID
     * @param newStatus 新状态
     * @return 更新后的任务
     * @throws IllegalArgumentException 任务不存在或状态非法
     */
    public Task updateStatus(int taskId, String newStatus) {
        Task task = requireTask(taskId);
        Task.validateStatus(newStatus);

        Task updated = task.withStatus(newStatus);
        store.save(updated);

        if (Task.STATUS_COMPLETED.equals(newStatus)) {
            clearDependency(taskId);
        }

        log.info("Updated task #{} status: {} -> {}", taskId, task.status(), newStatus);
        return updated;
    }

    /**
     * 更新任务描述。
     *
     * @param taskId      任务 ID
     * @param description 新描述
     * @return 更新后的任务
     */
    public Task updateDescription(int taskId, String description) {
        Task task = requireTask(taskId);
        Task updated = task.withDescription(description);
        store.save(updated);
        return updated;
    }

    /**
     * 认领任务（设置 owner，状态变为 in_progress）。
     *
     * @param taskId 任务 ID
     * @param owner  认领者标识
     * @return 更新后的任务
     * @throws IllegalStateException 任务被阻塞
     */
    public Task claim(int taskId, String owner) {
        Task task = requireTask(taskId);
        if (task.isBlocked()) {
            throw new IllegalStateException(
                    "Task #" + taskId + " is blocked by tasks: " + task.blockedBy());
        }
        Task updated = task.withOwner(owner).withStatus(Task.STATUS_IN_PROGRESS);
        store.save(updated);
        return updated;
    }

    /**
     * 列出所有任务。
     */
    public List<Task> listAll() {
        return store.listAll();
    }

    /**
     * 列出可认领的任务（pending + 无阻塞依赖 + 无 owner）。
     */
    public List<Task> listUnclaimed() {
        return store.listAll().stream()
                .filter(t -> Task.STATUS_PENDING.equals(t.status()))
                .filter(t -> !t.isBlocked())
                .filter(t -> t.owner().isEmpty())
                .toList();
    }

    /**
     * 获取任务详情。
     *
     * @param taskId 任务 ID
     * @return 任务（不存在则返回空）
     */
    public Optional<Task> get(int taskId) {
        return store.load(taskId);
    }

    /**
     * 格式化所有任务为文本报告。
     */
    public String formatAsText() {
        List<Task> tasks = listAll();
        if (tasks.isEmpty()) {
            return "No tasks.";
        }
        var sb = new StringBuilder();
        sb.append("Tasks (").append(tasks.size()).append(" total):\n\n");
        for (Task task : tasks) {
            sb.append(task.formatAsText()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 完成任务时，从所有下游任务的 blockedBy 中移除本任务 ID。
     */
    private void clearDependency(int completedId) {
        store.listAll().stream()
                .filter(t -> t.blockedBy().contains(completedId))
                .forEach(t -> {
                    var newBlockedBy = new ArrayList<>(t.blockedBy());
                    newBlockedBy.remove(Integer.valueOf(completedId));
                    store.save(t.withBlockedBy(List.copyOf(newBlockedBy)));
                    log.debug("Cleared dependency #{} from task #{}", completedId, t.id());
                });
    }

    /**
     * DFS 循环依赖检测。
     *
     * <p>从新任务的 blockedBy 出发，沿依赖链向上追溯，
     * 如果回到新任务自身 ID 则说明存在环。</p>
     *
     * @throws IllegalArgumentException 检测到循环依赖
     */
    private void detectCycle(int newTaskId, List<Integer> blockedBy) {
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>(blockedBy);

        while (!stack.isEmpty()) {
            int current = stack.pop();
            if (current == newTaskId) {
                throw new IllegalArgumentException(
                        "Circular dependency detected: task #" + newTaskId
                                + " would create a cycle through its blockedBy chain");
            }
            if (visited.add(current)) {
                store.load(current).ifPresent(t -> t.blockedBy().forEach(stack::push));
            }
        }
    }

    private Task requireTask(int taskId) {
        return store.load(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task #" + taskId + " not found"));
    }
}
