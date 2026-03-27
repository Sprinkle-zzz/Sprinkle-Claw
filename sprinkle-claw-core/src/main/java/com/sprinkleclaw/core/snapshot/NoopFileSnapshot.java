package com.sprinkleclaw.core.snapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 默认空实现，不追踪任何变更。
 * 当用户未启用文件快照功能时使用。
 *
 * @author sprinkle
 * @since 2026/3/26
 */
public final class NoopFileSnapshot implements FileSnapshot {

    public static final NoopFileSnapshot INSTANCE = new NoopFileSnapshot();

    @Override
    public void snapshot(Path filePath, OperationType operation, String description) {
    }

    @Override
    public Optional<String> undo() {
        return Optional.empty();
    }

    @Override
    public Optional<String> redo() {
        return Optional.empty();
    }

    @Override
    public List<ChangeRecord> history(int limit) {
        return List.of();
    }

    @Override
    public List<ChangeRecord> fileHistory(Path filePath, int limit) {
        return List.of();
    }
}
