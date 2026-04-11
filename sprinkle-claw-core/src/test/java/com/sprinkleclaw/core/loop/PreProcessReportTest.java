package com.sprinkleclaw.core.loop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreProcessReportTest {

    @Test
    void hadWorkReturnsFalseWhenNoWork() {
        var report = new PreProcessReport.Builder(1).build();
        assertThat(report.hadWork()).isFalse();
    }

    @Test
    void hadWorkReturnsTrueWhenTruncated() {
        var report = new PreProcessReport.Builder(1)
                .toolResultBudget(2)
                .build();
        assertThat(report.hadWork()).isTrue();
    }

    @Test
    void hadWorkReturnsTrueWhenMicroSaved() {
        var report = new PreProcessReport.Builder(1)
                .microCompact(new PreProcessReport.MicroCompactResult(1, 0, 50))
                .build();
        assertThat(report.hadWork()).isTrue();
    }

    @Test
    void hadWorkReturnsTrueWhenPruned() {
        var report = new PreProcessReport.Builder(1)
                .pruneCompact(new PreProcessReport.CompactResult(1000, 500, 0.5))
                .build();
        assertThat(report.hadWork()).isTrue();
    }

    @Test
    void hadWorkReturnsTrueWhenNotifications() {
        var report = new PreProcessReport.Builder(1)
                .notificationsDrained(3)
                .build();
        assertThat(report.hadWork()).isTrue();
    }

    @Test
    void totalTokensSavedAggregatesAllStages() {
        var report = new PreProcessReport.Builder(1)
                .microCompact(new PreProcessReport.MicroCompactResult(2, 1, 100))
                .pruneCompact(new PreProcessReport.CompactResult(500, 300, 0.4))
                .autoCompact(new PreProcessReport.CompactResult(300, 100, 0.67))
                .build();
        // micro: 100, prune: 200, auto: 200 = 500
        assertThat(report.totalTokensSaved()).isEqualTo(500);
    }

    @Test
    void builderSetsIteration() {
        var report = new PreProcessReport.Builder(5).build();
        assertThat(report.iteration()).isEqualTo(5);
    }

    @Test
    void durationIsNonNull() {
        var report = new PreProcessReport.Builder(1).build();
        assertThat(report.totalDuration()).isNotNull();
    }
}
