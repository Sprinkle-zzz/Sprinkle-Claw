package com.sprinkleclaw.core.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件时间戳缓存，记录 Agent 最后读取/写入文件时的 lastModified。
 *
 * <p>用于在编辑前检测文件是否被外部修改。使用 {@link ConcurrentHashMap} 保证线程安全，
 * 支持多工具并发调用 {@link #record} 和 {@link #validate}。</p>
 *
 * <p>存储在 {@code AgentContext.attributes} 中，键为 {@link #CONTEXT_KEY}。</p>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public final class FileTimestampCache {

    /**
     * 在 ToolContext.attributes 中的存储键。
     */
    public static final String CONTEXT_KEY = "fileTimestampCache";

    private final Map<Path, FileTime> cache = new ConcurrentHashMap<>();

    /**
     * 记录文件的最后修改时间。
     * 在 read_file 和 write_file/edit_file 成功后调用。
     *
     * @param filePath 文件绝对路径或相对路径
     */
    public void record(Path filePath) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(filePath);
            cache.put(filePath.toAbsolutePath().normalize(), lastModified);
        } catch (IOException e) {
            // 文件不存在或无法读取，不缓存
        }
    }

    /**
     * 校验文件是否被外部修改。
     *
     * @param filePath 要校验的文件路径
     * @return 校验结果
     */
    public ValidationResult validate(Path filePath) {
        Path normalized = filePath.toAbsolutePath().normalize();
        FileTime cached = cache.get(normalized);

        if (cached == null) {
            return ValidationResult.UNCACHED;
        }

        try {
            FileTime current = Files.getLastModifiedTime(normalized);
            return current.equals(cached)
                    ? ValidationResult.UNCHANGED
                    : ValidationResult.EXTERNALLY_MODIFIED;
        } catch (IOException e) {
            return ValidationResult.FILE_NOT_FOUND;
        }
    }

    /**
     * 清除指定文件的缓存。
     *
     * @param filePath 文件路径
     */
    public void invalidate(Path filePath) {
        cache.remove(filePath.toAbsolutePath().normalize());
    }

    /**
     * 文件校验结果。
     */
    public enum ValidationResult {
        /**
         * 文件未被修改，可以安全编辑
         */
        UNCHANGED,
        /**
         * 文件被外部修改，需要警告
         */
        EXTERNALLY_MODIFIED,
        /**
         * 文件未在缓存中（首次编辑）
         */
        UNCACHED,
        /**
         * 文件不存在
         */
        FILE_NOT_FOUND
    }
}
