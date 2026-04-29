package icu.sprinkle.loom.tool.builtin;

import icu.sprinkle.loom.tool.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBehaviorMarkersTest {

    @Test
    void readFileToolIsReadOnlyAndConcurrencySafe() {
        var tool = new ReadFileTool();
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.isConcurrencySafe()).isTrue();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void writeFileToolIsConcurrencySafe() {
        var tool = new WriteFileTool();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isConcurrencySafe()).isTrue();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void editFileToolIsConcurrencySafe() {
        var tool = new EditFileTool();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isConcurrencySafe()).isTrue();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void bashToolIsExclusive() {
        var tool = new BashTool();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isConcurrencySafe()).isFalse();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void todoWriteToolIsConcurrencySafe() {
        var tool = new TodoWriteTool();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isConcurrencySafe()).isTrue();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void compactToolIsLowRisk() {
        var tool = new CompactTool();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isConcurrencySafe()).isFalse();
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.LOW);
    }
}
