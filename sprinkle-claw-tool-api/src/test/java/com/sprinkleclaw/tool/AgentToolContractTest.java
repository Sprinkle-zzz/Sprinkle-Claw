package com.sprinkleclaw.tool;

import com.sprinkleclaw.protocol.tool.ToolDefinition;
import com.sprinkleclaw.protocol.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Agent Tool SPI 契约测试基类。
 * <p>所有 {@link AgentTool} 实现需继承此类并提供具体工具实例，
 * 以验证实现符合 SPI 契约。</p>
 *
 * <p>子类需实现 {@link #createTool()} 和 {@link #validInput()} 方法。</p>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
public abstract class AgentToolContractTest {

    @TempDir
    Path tempDir;

    /**
     * 子类提供待测试的工具实例。
     *
     * @return AgentTool 实例
     */
    protected abstract AgentTool createTool();

    /**
     * 子类提供该工具的一组有效输入参数。
     *
     * @return 有效输入 Map
     */
    protected abstract Map<String, Object> validInput();

    @Test
    void definitionShouldNotBeNull() {
        AgentTool tool = createTool();
        ToolDefinition def = tool.definition();

        assertThat(def).isNotNull();
        assertThat(def.name()).isNotBlank();
        assertThat(def.description()).isNotBlank();
        assertThat(def.inputSchema()).isNotNull();
        assertThat(def.inputSchema()).containsKey("type");
    }

    @Test
    void nameShouldMatchDefinition() {
        AgentTool tool = createTool();
        assertThat(tool.name()).isEqualTo(tool.definition().name());
    }

    @Test
    void executeShouldReturnNonNullResult() {
        AgentTool tool = createTool();
        ToolContext context = new ToolContext(tempDir);

        ToolResult result = tool.execute(validInput(), context);

        assertThat(result).isNotNull();
        assertThat(result.output()).isNotNull();
    }

    @Test
    void executeWithEmptyInputShouldNotThrow() {
        AgentTool tool = createTool();
        ToolContext context = new ToolContext(tempDir);

        assertThatCode(() -> tool.execute(Map.of(), context))
                .doesNotThrowAnyException();
    }

    @Test
    void inputSchemaShouldBeValidJsonSchema() {
        AgentTool tool = createTool();
        Map<String, Object> schema = tool.definition().inputSchema();

        assertThat(schema.get("type")).isEqualTo("object");
    }

    @Test
    void toolShouldBeReusable() {
        AgentTool tool = createTool();
        ToolContext context = new ToolContext(tempDir);
        Map<String, Object> input = validInput();

        ToolResult result1 = tool.execute(input, context);
        ToolResult result2 = tool.execute(input, context);

        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
    }
}
