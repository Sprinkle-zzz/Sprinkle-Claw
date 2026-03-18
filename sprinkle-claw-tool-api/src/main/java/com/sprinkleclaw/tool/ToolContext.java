package com.sprinkleclaw.tool;

import java.nio.file.Path;

/**
 * 工具执行上下文，传递给每次工具调用的不可变环境信息。
 *
 * @param workingDirectory 当前工作目录
 *
 * @author sprinkle
 * @since 2026/3/18
 */
public record ToolContext(Path workingDirectory) {
}
