package icu.sprinkle.loom.workflow.agent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 接口的方法元数据缓存。解析一次，终生复用。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class AgentMethodCache {

    private static final ConcurrentHashMap<Class<?>, Map<Method, AgentMethodMetadata>> CACHE
            = new ConcurrentHashMap<>();

    static Map<Method, AgentMethodMetadata> get(Class<?> agentInterface) {
        return CACHE.computeIfAbsent(agentInterface, AgentMethodCache::parse);
    }

    private static Map<Method, AgentMethodMetadata> parse(Class<?> agentInterface) {
        Agent agentAnnotation = agentInterface.getAnnotation(Agent.class);
        var result = new ConcurrentHashMap<Method, AgentMethodMetadata>();
        for (Method m : agentInterface.getDeclaredMethods()) {
            if (m.isDefault()) continue;
            AgentMethodValidator.validate(m);
            result.put(m, new AgentMethodMetadata(m, agentAnnotation));
        }
        return Map.copyOf(result);
    }
}
