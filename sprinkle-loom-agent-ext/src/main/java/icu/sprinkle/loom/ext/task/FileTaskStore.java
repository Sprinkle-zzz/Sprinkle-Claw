package icu.sprinkle.loom.ext.task;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于文件系统的任务持久化实现。
 *
 * <p>每个任务存储为独立的 JSON 文件：{@code .tasks/task_{id}.json}</p>
 *
 * <p>并发安全：{@link AtomicInteger} 保证 ID 唯一性；
 * 工具层通过 {@code supportsConcurrency()=false} 保证 task 工具串行执行。</p>
 *
 * @author sprinkle
 * @since 2026/4/3
 */
public final class FileTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(FileTaskStore.class);

    private final Path tasksDir;
    private final ObjectMapper mapper;
    private final AtomicInteger idCounter;

    public FileTaskStore(Path tasksDir) {
        this.tasksDir = tasksDir;
        this.mapper = buildMapper();
        this.idCounter = new AtomicInteger(0);
        initialize();
    }

    private ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private void initialize() {
        try {
            Files.createDirectories(tasksDir);
            // 扫描已有任务文件，确定起始 ID
            int maxId = listAll().stream()
                    .mapToInt(Task::id)
                    .max()
                    .orElse(0);
            idCounter.set(maxId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize tasks directory: " + tasksDir, e);
        }
    }

    @Override
    public void save(Task task) {
        Path file = taskFile(task.id());
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save task #" + task.id(), e);
        }
    }

    @Override
    public Optional<Task> load(int taskId) {
        Path file = taskFile(taskId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), Task.class));
        } catch (IOException e) {
            log.warn("Failed to load task #{}: {}", taskId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(int taskId) {
        try {
            Files.deleteIfExists(taskFile(taskId));
        } catch (IOException e) {
            log.warn("Failed to delete task #{}: {}", taskId, e.getMessage());
        }
    }

    @Override
    public List<Task> listAll() {
        try (var files = Files.list(tasksDir)) {
            return files
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        return name.startsWith("task_") && name.endsWith(".json");
                    })
                    .map(f -> {
                        try {
                            return mapper.readValue(f.toFile(), Task.class);
                        } catch (IOException e) {
                            log.warn("Failed to parse task file {}: {}", f, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(Task::id))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public int nextId() {
        return idCounter.incrementAndGet();
    }

    private Path taskFile(int taskId) {
        return tasksDir.resolve("task_" + taskId + ".json");
    }
}
