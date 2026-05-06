package icu.sprinkle.loom.workflow.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 资源中加载声明式 Agent 使用的 prompt 模板。
 * <p>
 * 该工具类保持包内可见，因为资源解析只是 {@link AgentFactory} 和
 * {@link AgentMethodMetadata} 的内部实现细节；公开扩展点应继续收敛在注解
 * 与工厂配置上。
 *
 * @author sprinkle
 * @since 2026/5/6
 */
final class PromptResources {

    private PromptResources() {
    }

    /**
     * 将注解中的内联 prompt 和可选 classpath 资源解析为最终 prompt。
     * <p>
     * 当内联内容和资源路径同时存在时，内联内容在前，资源内容在空行后追加。
     * 这样既保留了已有内联 prompt 作为更高层指令的语义，也允许较长模板放到资源文件中维护。
     *
     * @param anchor 读取资源时优先使用其类加载器的锚点类型
     * @param inline 注解中的内联 prompt，可为空或 {@code null}
     * @param resource 注解中的 classpath 资源路径，可为空或 {@code null}
     * @return 解析后的 prompt；当两种来源都不存在时返回空字符串
     * @throws IllegalArgumentException 配置了资源但无法找到或读取时抛出
     */
    static String resolve(Class<?> anchor, String inline, String resource) {
        return resolve(anchor, inline != null ? new String[]{inline} : new String[0], "\n", resource);
    }

    /**
     * 将多段内联 prompt 和可选 classpath 资源解析为最终 prompt。
     * <p>
     * 多段内联 prompt 会先按 delimiter 拼接，再与资源内容组合。delimiter 只作用于
     * 注解内的多段文本，不影响内联 prompt 与资源 prompt 之间的空行分隔。
     *
     * @param anchor 读取资源时优先使用其类加载器的锚点类型
     * @param inlineParts 注解中的多段内联 prompt，可为空
     * @param delimiter 多段内联 prompt 的拼接分隔符；为 {@code null} 时按换行处理
     * @param resource 注解中的 classpath 资源路径，可为空或 {@code null}
     * @return 解析后的 prompt；当两种来源都不存在时返回空字符串
     * @throws IllegalArgumentException 配置了资源但无法找到或读取时抛出
     */
    static String resolve(Class<?> anchor, String[] inlineParts, String delimiter, String resource) {
        String inline = join(inlineParts, delimiter);
        if (resource == null || resource.isBlank()) {
            return inline;
        }
        String loaded = load(anchor, resource);
        if (!inline.isBlank()) {
            return inline + "\n\n" + loaded;
        }
        return loaded;
    }

    private static String join(String[] parts, String delimiter) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        String actualDelimiter = delimiter != null ? delimiter : "\n";
        return String.join(actualDelimiter, parts);
    }

    /**
     * 按 UTF-8 读取 classpath prompt 资源。
     *
     * @param anchor 回退到其他类加载器前优先尝试的锚点类型
     * @param resource 资源路径，可带或不带前导斜杠
     * @return 去除尾部空白后的资源内容
     */
    private static String load(Class<?> anchor, String resource) {
        String normalized = resource.startsWith("/") ? resource.substring(1) : resource;

        // 优先使用声明 Agent 接口的类加载器，方便使用自定义类加载器的应用
        // 将 prompt 文件和 Agent 定义放在同一 classpath 边界内。
        ClassLoader loader = anchor != null ? anchor.getClassLoader() : null;
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            loader = PromptResources.class.getClassLoader();
        }

        try (InputStream in = loader.getResourceAsStream(normalized)) {
            if (in == null) {
                throw new IllegalArgumentException("Prompt resource not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read prompt resource: " + resource, e);
        }
    }
}
