package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class EvaluationResultTest {

    @Test
    void matchedReturnsTrueWhenRuleIdsNonEmpty() {
        EvaluationResult result = new EvaluationResult(List.of("r1"), List.of("r1"), Map.of());
        Assertions.assertThat(result.matched()).isTrue();
    }

    @Test
    void verdictReturnsMatchedWhenAtLeastOneRuleMatches() {
        EvaluationResult result = new EvaluationResult(List.of("r1"), List.of("r1"), Map.of("r2", "error"));
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
    }

    @Test
    void verdictReturnsErroredWhenNoMatchButErrorsExist() {
        EvaluationResult result = new EvaluationResult(List.of(), List.of("r1"), Map.of("r3", "boom"));
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.ERRORED);
    }

    @Test
    void verdictReturnsUnmatchedWhenNoMatchAndNoErrors() {
        EvaluationResult result = new EvaluationResult(List.of(), List.of("r1"), Map.of());
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
    }

    @Test
    void nullCollectionsAreHandledAsEmpty() {
        EvaluationResult result = new EvaluationResult(null, null, null);
        Assertions.assertThat(result.matchedRuleIds()).isEmpty();
        Assertions.assertThat(result.matched()).isFalse();
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
    }

    @Test
    void collectionsAreDefensivelyCopiedAndUnmodifiable() {
        List<String> input = new ArrayList<>();
        input.add("r1");
        EvaluationResult result = new EvaluationResult(input, List.of("r1"), Map.of());
        Assertions.assertThat(result.matchedRuleIds()).containsExactly("r1");

        input.add("r2");
        Assertions.assertThat(result.matchedRuleIds()).doesNotContain("r2");

        Assertions.assertThatThrownBy(() -> result.matchedRuleIds().add("r3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
