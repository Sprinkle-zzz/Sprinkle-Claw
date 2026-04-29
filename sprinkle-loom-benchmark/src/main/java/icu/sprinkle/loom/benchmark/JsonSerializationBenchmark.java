package icu.sprinkle.loom.benchmark;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JSON 序列化/反序列化基准测试。
 * <p>测量 ChatResponse 和工具调用的 Jackson 序列化性能。</p>
 *
 * @author sprinkle
 * @since 2026/3/21
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonSerializationBenchmark {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ContentBlock.TextBlock.class, name = "text"),
            @JsonSubTypes.Type(value = ContentBlock.ToolUseBlock.class, name = "tool_use"),
            @JsonSubTypes.Type(value = ContentBlock.ThinkingBlock.class, name = "thinking")
    })
    abstract static class ContentBlockMixin {
    }

    private ObjectMapper mapper;
    private ChatResponse response;
    private String responseJson;

    @Setup
    public void setup() throws Exception {
        mapper = new ObjectMapper();
        mapper.addMixIn(ContentBlock.class, ContentBlockMixin.class);

        response = new ChatResponse(
                List.of(
                        new ContentBlock.TextBlock("Here is my analysis of the code..."),
                        new ContentBlock.ToolUseBlock("call_001", "read_file",
                                Map.of("path", "src/main/java/App.java", "offset", 1, "limit", 50))
                ),
                StopReason.TOOL_USE,
                new Usage(1500, 300),
                "claude-opus-4-7"
        );

        responseJson = mapper.writeValueAsString(response);
    }

    @Benchmark
    public String serializeChatResponse() throws Exception {
        return mapper.writeValueAsString(response);
    }

    @Benchmark
    public ChatResponse deserializeChatResponse() throws Exception {
        return mapper.readValue(responseJson, ChatResponse.class);
    }
}
