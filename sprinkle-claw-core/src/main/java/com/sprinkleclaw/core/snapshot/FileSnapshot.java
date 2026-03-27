package com.sprinkleclaw.core.snapshot;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文件快照 SPI 接口，追踪 Agent 的文件变更并支持 Undo/Redo。
 *
 * <p>实现方需要保证：</p>
 * <ol>
 *   <li>{@link #snapshot} 在文件写入后立即调用，不可延迟</li>
 *   <li>undo/redo 操作原子性——要么完全回退/重做，要么不执行</li>
 *   <li>不影响项目本身的版本控制系统</li>
 * </ol>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public interface FileSnapshot {

    /**
     * 记录文件变更快照。
     *
     * @param filePath    被修改的文件路径（相对于工作目录）
     * @param operation   操作类型
     * @param description 变更描述（如 "write_file: 创建 hello.txt"）
     */
    void snapshot(Path filePath, OperationType operation, String description);

    /**
     * 撤销最近一次文件变更。
     *
     * @return 被撤销的变更描述，无可撤销操作时返回 empty
     */
    Optional<String> undo();

    /**
     * 重做最近一次被撤销的变更。
     *
     * @return 被重做的变更描述，无可重做操作时返回 empty
     */
    Optional<String> redo();

    /**
     * 获取变更历史。
     *
     * @param limit 最近 N 条
     * @return 变更记录列表（时间倒序）
     */
    List<ChangeRecord> history(int limit);

    /**
     * 获取指定文件的变更历史。
     *
     * @param filePath 文件路径
     * @param limit    最近 N 条
     * @return 变更记录列表
     */
    List<ChangeRecord> fileHistory(Path filePath, int limit);

    /**
     * 操作类型。
     */
    enum OperationType {
        /**
         * 新建文件
         */
        CREATE,
        /**
         * 修改文件
         */
        MODIFY,
        /**
         * 删除文件
         */
        DELETE
    }

    /**
     * 变更记录。
     *
     * @param commitId    Git 提交 ID
     * @param filePath    文件路径
     * @param operation   操作类型
     * @param description 变更描述
     * @param timestamp   变更时间
     */
    record ChangeRecord(
            String commitId,
            Path filePath,
            OperationType operation,
            String description,
            Instant timestamp
    ) {
    }
}
