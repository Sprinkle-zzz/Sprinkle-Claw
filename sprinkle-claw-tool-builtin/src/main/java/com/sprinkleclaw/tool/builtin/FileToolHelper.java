package com.sprinkleclaw.tool.builtin;

import com.sprinkleclaw.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

/**
 * 文件工具辅助类，通过 ToolContext 的通用属性桥接 core 模块的
 * FileTimestampCache 和 FileSnapshot 功能。
 *
 * <p>由于 tool-builtin 不依赖 core 模块，此类使用 MethodHandle 反射调用。
 * 初始化开销仅在首次使用时发生，后续调用接近直接调用性能。</p>
 *
 * @author sprinkle
 * @since 2026/3/26
 */
final class FileToolHelper {

    private static final Logger log = LoggerFactory.getLogger(FileToolHelper.class);

    private static final String TIMESTAMP_CACHE_KEY = "fileTimestampCache";
    private static final String FILE_SNAPSHOT_KEY = "fileSnapshot";

    private static volatile MethodHandle recordHandle;
    private static volatile MethodHandle validateHandle;
    private static volatile MethodHandle snapshotHandle;

    private FileToolHelper() {
    }

    /**
     * 记录文件的最后修改时间。在 read_file、write_file、edit_file 成功后调用。
     *
     * @param context  工具上下文
     * @param filePath 文件绝对路径
     */
    static void recordTimestamp(ToolContext context, Path filePath) {
        Object cache = context.getAttribute(TIMESTAMP_CACHE_KEY);
        if (cache == null) {
            return;
        }
        try {
            MethodHandle mh = getRecordHandle(cache.getClass());
            if (mh != null) {
                mh.invoke(cache, filePath);
            }
        } catch (Throwable e) {
            log.debug("recordTimestamp 调用失败", e);
        }
    }

    /**
     * 校验文件是否被外部修改。
     *
     * @param context  工具上下文
     * @param filePath 文件绝对路径
     * @return 校验结果名称（UNCHANGED/EXTERNALLY_MODIFIED/UNCACHED/FILE_NOT_FOUND），
     * 无缓存时返回 null
     */
    static String validateTimestamp(ToolContext context, Path filePath) {
        Object cache = context.getAttribute(TIMESTAMP_CACHE_KEY);
        if (cache == null) {
            return null;
        }
        try {
            MethodHandle mh = getValidateHandle(cache.getClass());
            if (mh != null) {
                Object result = mh.invoke(cache, filePath);
                return result != null ? result.toString() : null;
            }
        } catch (Throwable e) {
            log.debug("validateTimestamp 调用失败", e);
        }
        return null;
    }

    /**
     * 记录文件变更快照。
     *
     * @param context     工具上下文
     * @param workdir     工作目录
     * @param filePath    文件绝对路径
     * @param isCreate    是否为创建操作
     * @param description 变更描述
     */
    static void takeSnapshot(ToolContext context, Path workdir, Path filePath,
                             boolean isCreate, String description) {
        Object snapshot = context.getAttribute(FILE_SNAPSHOT_KEY);
        if (snapshot == null) {
            return;
        }
        try {
            MethodHandle mh = getSnapshotHandle(snapshot.getClass());
            if (mh != null) {
                Path relativePath = workdir.relativize(filePath);
                // OperationType enum: CREATE=0, MODIFY=1, DELETE=2
                Object opType = findOperationType(snapshot.getClass(), isCreate ? "CREATE" : "MODIFY");
                if (opType != null) {
                    mh.invoke(snapshot, relativePath, opType, description);
                }
            }
        } catch (Throwable e) {
            log.debug("takeSnapshot 调用失败", e);
        }
    }

    private static MethodHandle getRecordHandle(Class<?> cacheClass) {
        MethodHandle mh = recordHandle;
        if (mh == null) {
            synchronized (FileToolHelper.class) {
                mh = recordHandle;
                if (mh == null) {
                    try {
                        mh = MethodHandles.lookup().findVirtual(
                                cacheClass, "record", MethodType.methodType(void.class, Path.class));
                        recordHandle = mh;
                    } catch (Exception e) {
                        log.warn("无法获取 record MethodHandle", e);
                    }
                }
            }
        }
        return mh;
    }

    private static MethodHandle getValidateHandle(Class<?> cacheClass) {
        MethodHandle mh = validateHandle;
        if (mh == null) {
            synchronized (FileToolHelper.class) {
                mh = validateHandle;
                if (mh == null) {
                    try {
                        // validate 返回 enum，先找到 validate 方法
                        var method = cacheClass.getMethod("validate", Path.class);
                        mh = MethodHandles.lookup().unreflect(method);
                        validateHandle = mh;
                    } catch (Exception e) {
                        log.warn("无法获取 validate MethodHandle", e);
                    }
                }
            }
        }
        return mh;
    }

    private static MethodHandle getSnapshotHandle(Class<?> snapshotClass) {
        MethodHandle mh = snapshotHandle;
        if (mh == null) {
            synchronized (FileToolHelper.class) {
                mh = snapshotHandle;
                if (mh == null) {
                    try {
                        var method = snapshotClass.getMethod("snapshot", Path.class,
                                findOperationTypeClass(snapshotClass), String.class);
                        mh = MethodHandles.lookup().unreflect(method);
                        snapshotHandle = mh;
                    } catch (Exception e) {
                        log.warn("无法获取 snapshot MethodHandle", e);
                    }
                }
            }
        }
        return mh;
    }

    @SuppressWarnings("unchecked")
    private static Object findOperationType(Class<?> snapshotClass, String name) {
        try {
            Class<?> opTypeClass = findOperationTypeClass(snapshotClass);
            if (opTypeClass != null && opTypeClass.isEnum()) {
                for (Object c : opTypeClass.getEnumConstants()) {
                    if (c.toString().equals(name)) {
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("查找 OperationType 失败", e);
        }
        return null;
    }

    private static Class<?> findOperationTypeClass(Class<?> snapshotClass) {
        for (Class<?> inner : snapshotClass.getDeclaringClass() != null
                ? snapshotClass.getDeclaringClass().getDeclaredClasses()
                : snapshotClass.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("OperationType")) {
                return inner;
            }
        }
        // 如果是接口实现类，查找接口的内部类
        for (Class<?> iface : snapshotClass.getInterfaces()) {
            for (Class<?> inner : iface.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("OperationType")) {
                    return inner;
                }
            }
        }
        return null;
    }
}
