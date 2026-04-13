package com.sprinkleclaw.workflow.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMethodValidatorTest {

    // ── 测试用接口 ──────────────────────────────────────────────

    @Agent
    interface ValidSingleParam {
        String greet(String name);
    }

    @Agent
    interface VoidReturn {
        void doSomething(String x);
    }

    @Agent
    interface NoParamsNoAnnotation {
        String generate();
    }

    @Agent
    interface NoParamsWithUserMessage {
        @UserMessage("Tell me a joke")
        String joke();
    }

    @Agent
    interface MultiParamNoUserMessage {
        String translate(String text, String lang);
    }

    @Agent
    interface MultiParamWithUserMessage {
        String translate(@UserMessage String text, String lang);
    }

    // ── 测试 ────────────────────────────────────────────────────

    @Test
    void validate_voidReturn_throws() throws Exception {
        Method m = VoidReturn.class.getDeclaredMethod("doSomething", String.class);
        assertThatThrownBy(() -> AgentMethodValidator.validate(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("void");
    }

    @Test
    void validate_noParams_noUserMessage_throws() throws Exception {
        Method m = NoParamsNoAnnotation.class.getDeclaredMethod("generate");
        assertThatThrownBy(() -> AgentMethodValidator.validate(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one parameter");
    }

    @Test
    void validate_noParams_withUserMessage_passes() throws Exception {
        Method m = NoParamsWithUserMessage.class.getDeclaredMethod("joke");
        assertThatCode(() -> AgentMethodValidator.validate(m)).doesNotThrowAnyException();
    }

    @Test
    void validate_singleParam_passes() throws Exception {
        Method m = ValidSingleParam.class.getDeclaredMethod("greet", String.class);
        assertThatCode(() -> AgentMethodValidator.validate(m)).doesNotThrowAnyException();
    }

    @Test
    void validate_multipleParams_noUserMessage_throws() throws Exception {
        Method m = MultiParamNoUserMessage.class.getDeclaredMethod("translate", String.class, String.class);
        assertThatThrownBy(() -> AgentMethodValidator.validate(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@UserMessage");
    }

    @Test
    void validate_multipleParams_withUserMessageParam_passes() throws Exception {
        Method m = MultiParamWithUserMessage.class.getDeclaredMethod("translate", String.class, String.class);
        assertThatCode(() -> AgentMethodValidator.validate(m)).doesNotThrowAnyException();
    }
}
