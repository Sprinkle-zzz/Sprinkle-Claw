package icu.sprinkle.loom.ext.task;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 持久化任务记录。
 *
 * @param id          任务 ID（自增整数）
 * @param subject     任务标题
 * @param description 任务详细描述
 * @param status      任务状态：pending | in_progress | completed | cancelled
 * @param owner       认领者（预留 MVP7 多 Agent 场景）
 * @param worktree    绑定的工作树名（预留 MVP7）
 * @param blockedBy   依赖的任务 ID 列表（这些任务完成后本任务才可执行）
 * @param blocks      被本任务阻塞的任务 ID 列表（本任务完成后解锁这些任务）
 * @param createdAt   创建时间
 * @param updatedAt   最后更新时间
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public record Task(
        int id,
        String subject,
        String description,
        String status,
        String owner,
        String worktree,
        List<Integer> blockedBy,
        List<Integer> blocks,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final Set<String> VALID_STATUSES = Set.of(
            STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_COMPLETED, STATUS_CANCELLED
    );

    /**
     * 校验状态值合法性。
     *
     * @throws IllegalArgumentException 状态非法
     */
    public static void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Invalid status: '" + status + "'. Valid values: " + VALID_STATUSES);
        }
    }

    /**
     * 任务是否处于终态（completed 或 cancelled）。
     */
    public boolean isTerminal() {
        return STATUS_COMPLETED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    /**
     * 任务是否被阻塞（存在未完成的依赖）。
     */
    public boolean isBlocked() {
        return !blockedBy.isEmpty();
    }

    /**
     * 创建更新状态后的副本。
     */
    public Task withStatus(String newStatus) {
        validateStatus(newStatus);
        return new Task(id, subject, description, newStatus, owner, worktree,
                blockedBy, blocks, createdAt, Instant.now());
    }

    /**
     * 创建更新 owner 后的副本。
     */
    public Task withOwner(String newOwner) {
        return new Task(id, subject, description, status, newOwner, worktree,
                blockedBy, blocks, createdAt, Instant.now());
    }

    /**
     * 创建更新 blockedBy 后的副本。
     */
    public Task withBlockedBy(List<Integer> newBlockedBy) {
        return new Task(id, subject, description, status, owner, worktree,
                newBlockedBy, blocks, createdAt, Instant.now());
    }

    /**
     * 创建更新 blocks 后的副本。
     */
    public Task withBlocks(List<Integer> newBlocks) {
        return new Task(id, subject, description, status, owner, worktree,
                blockedBy, newBlocks, createdAt, Instant.now());
    }

    /**
     * 创建更新描述后的副本。
     */
    public Task withDescription(String newDescription) {
        return new Task(id, subject, newDescription, status, owner, worktree,
                blockedBy, blocks, createdAt, Instant.now());
    }

    /**
     * 格式化为人类可读文本。
     */
    public String formatAsText() {
        var sb = new StringBuilder();
        sb.append("Task #").append(id).append(": ").append(subject).append("\n");
        sb.append("  Status: ").append(status);
        if (owner != null && !owner.isEmpty()) {
            sb.append(" (owner: ").append(owner).append(")");
        }
        sb.append("\n");
        if (description != null && !description.isEmpty()) {
            sb.append("  Description: ").append(description).append("\n");
        }
        if (!blockedBy.isEmpty()) {
            sb.append("  Blocked by: #").append(blockedBy.toString().replaceAll("[\\[\\] ]", "")).append("\n");
        }
        if (!blocks.isEmpty()) {
            sb.append("  Blocks: #").append(blocks.toString().replaceAll("[\\[\\] ]", "")).append("\n");
        }
        return sb.toString();
    }
}
