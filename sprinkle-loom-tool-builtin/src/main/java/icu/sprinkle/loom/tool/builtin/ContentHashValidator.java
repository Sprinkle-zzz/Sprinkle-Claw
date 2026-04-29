package icu.sprinkle.loom.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 SHA-256 的文件内容双重校验器。
 * <p>当 mtime 变化时，通过计算文件内容 hash 确认是否真正被外部修改，
 * 避免云同步工具（OneDrive/iCloud/Dropbox）修改 mtime 导致的误报。</p>
 *
 * <p>校验流程：
 * <ol>
 *   <li>mtime 未变 → 快速路径，返回未修改</li>
 *   <li>mtime 变了 → 比对文件大小</li>
 *   <li>大小相同 → 计算 SHA-256 对比</li>
 *   <li>hash 相同 → mtime 误报，更新缓存 mtime</li>
 *   <li>hash 不同 → 文件确实被外部修改</li>
 * </ol></p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public class ContentHashValidator {

    private static final Logger log = LoggerFactory.getLogger(ContentHashValidator.class);

    private final ConcurrentHashMap<Path, FileFingerprint> fingerprints = new ConcurrentHashMap<>();

    /**
     * 文件指纹。
     */
    public record FileFingerprint(FileTime mtime, String sha256, long size) {
    }

    /**
     * 检查文件是否被外部修改。
     *
     * @param file 文件路径
     * @return true 如果文件内容确实改变
     */
    public boolean isExternallyModified(Path file) {
        FileFingerprint cached = fingerprints.get(file);
        if (cached == null) return false;

        try {
            FileTime currentMtime = Files.getLastModifiedTime(file);

            // 快速路径：mtime 未变 → 未修改
            if (currentMtime.equals(cached.mtime())) {
                return false;
            }

            // mtime 变了 → 比对文件大小
            long currentSize = Files.size(file);
            if (currentSize != cached.size()) {
                return true;
            }

            // 大小相同 → 计算 hash 确认
            String currentHash = computeSha256(file);
            if (currentHash.equals(cached.sha256())) {
                // hash 相同 → mtime 误报，更新缓存的 mtime
                fingerprints.put(file, new FileFingerprint(currentMtime, cached.sha256(), cached.size()));
                return false;
            }

            return true;
        } catch (IOException e) {
            log.warn("Failed to check file modification: {}", file, e);
            return true;
        }
    }

    /**
     * 记录文件的当前指纹（写入后调用）。
     */
    public void recordFingerprint(Path file) {
        try {
            fingerprints.put(file, new FileFingerprint(
                    Files.getLastModifiedTime(file),
                    computeSha256(file),
                    Files.size(file)
            ));
        } catch (IOException e) {
            log.warn("Failed to record fingerprint: {}", file, e);
        }
    }

    /**
     * 移除文件跟踪。
     */
    public void untrack(Path file) {
        fingerprints.remove(file);
    }

    /**
     * 获取文件的缓存指纹（测试用）。
     */
    public FileFingerprint getFingerprint(Path file) {
        return fingerprints.get(file);
    }

    private String computeSha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
