package com.sprinkleclaw.core.memory;

import com.sprinkleclaw.api.Experimental;

import java.util.List;

/**
 * 长期记忆存储 SPI。
 *
 * <p>Agent 通过此接口记录和检索跨会话的知识。
 * {@link #retrieve(String, int)} 的检索策略由实现决定——
 * 可以是简单的关键词匹配，也可以是向量嵌入语义检索。</p>
 *
 * @author sprinkle
 * @since 2026/4/24
 */
@Experimental("MVP8 引入；检索语义（关键词 vs 向量）和分页 API 可能在 MVP10 调整")
public interface MemoryStore {

    /**
     * 记录一条记忆。
     */
    void record(MemoryEntry entry);

    /**
     * 根据查询检索相关记忆。
     *
     * @param query 查询文本
     * @param topK  最多返回条数
     * @return 按相关度降序排列的记忆列表
     */
    List<MemoryEntry> retrieve(String query, int topK);

    /**
     * 删除指定记忆。
     */
    void delete(String memoryId);

    /**
     * 列出所有记忆（按创建时间降序）。
     */
    List<MemoryEntry> listAll();

    /**
     * 记忆总数。
     */
    int size();
}
