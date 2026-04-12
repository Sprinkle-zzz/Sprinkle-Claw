package com.sprinkleclaw.workflow.agent;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 声明式 Agent 方法签名校验器。
 *
 * @author sprinkle
 * @since 2026/4/12
 */
final class AgentMethodValidator {

    static void validate(Method method) {
        // 跳过 Object 方法
        if (method.getDeclaringClass() == Object.class) return;

        // 返回类型不能是 void
        if (method.getReturnType() == void.class) {
            throw new IllegalArgumentException(
                    "Method " + method.getName() + " must have a return type (not void)");
        }

        // 至少有一个参数
        if (method.getParameterCount() == 0) {
            // 方法级 @UserMessage 也可以（无参数但有模板）
            if (!method.isAnnotationPresent(UserMessage.class)) {
                throw new IllegalArgumentException(
                        "Method " + method.getName() + " must have at least one parameter or a @UserMessage annotation");
            }
        }

        // 如果有多个参数且无方法级 @UserMessage，至少需要一个 @UserMessage 参数
        if (method.getParameterCount() > 1 && !method.isAnnotationPresent(UserMessage.class)) {
            boolean hasUserMessage = false;
            for (Parameter p : method.getParameters()) {
                if (p.isAnnotationPresent(UserMessage.class)) {
                    hasUserMessage = true;
                    break;
                }
            }
            if (!hasUserMessage) {
                throw new IllegalArgumentException(
                        "Method " + method.getName() + " with multiple parameters must have at least one @UserMessage parameter or a method-level @UserMessage template");
            }
        }
    }
}
