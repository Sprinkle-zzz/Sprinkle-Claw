package com.sprinkleclaw.core.snapshot;

/**
 * 文件快照操作异常。
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public class SnapshotException extends RuntimeException {

    public SnapshotException(String message) {
        super(message);
    }

    public SnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
