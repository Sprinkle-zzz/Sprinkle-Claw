package com.sprinkleclaw.ext.task;

import java.util.List;
import java.util.Optional;

/**
 * 任务持久化抽象接口（SPI）。
 *
 * <p>默认实现为 {@link FileTaskStore}，MVP7 可扩展数据库实现。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public interface TaskStore {

    /**
     * 保存任务（创建或更新）。
     *
     * @param task 要保存的任务
     */
    void save(Task task);

    /**
     * 按 ID 加载任务。
     *
     * @param taskId 任务 ID
     * @return 任务（不存在则返回空）
     */
    Optional<Task> load(int taskId);

    /**
     * 删除任务。
     *
     * @param taskId 任务 ID
     */
    void delete(int taskId);

    /**
     * 列出所有任务（按 ID 升序）。
     *
     * @return 任务列表
     */
    List<Task> listAll();

    /**
     * 获取并递增下一个可用任务 ID（线程安全）。
     *
     * @return 新任务 ID
     */
    int nextId();
}
