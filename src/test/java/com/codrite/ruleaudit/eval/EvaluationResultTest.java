package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class EvaluationResultTest {

    @Test
    void matchedReturnsTrueWhenRuleResultsContainMatch() {
        EvaluationResult result = new EvaluationResult(List.of(
            new RuleResult("r1", AuditType.MATCHED, "ok")
        ));
        Assertions.assertThat(result.matched()).isTrue();
    }

    @Test
    void verdictReturnsMatchedWhenAtLeastOneRuleMatches() {
        EvaluationResult result = new EvaluationResult(List.of(
            new RuleResult("r1", AuditType.MATCHED, "ok"),
            new RuleResult("r2", AuditType.ERRORED, "boom")
        ));
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
    }

    @Test
    void verdictReturnsErroredWhenNoMatchButErrorsExist() {
        EvaluationResult result = new EvaluationResult(List.of(
            new RuleResult("r1", AuditType.UNMATCHED, "no"),
            new RuleResult("r3", AuditType.ERRORED, "boom")
        ));
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.ERRORED);
    }

    @Test
    void verdictReturnsUnmatchedWhenNoMatchAndNoErrors() {
        EvaluationResult result = new EvaluationResult(List.of(
            new RuleResult("r1", AuditType.UNMATCHED, "no")
        ));
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
    }

    @Test
    void nullCollectionsAreHandledAsEmpty() {
        EvaluationResult result = new EvaluationResult(null);
        Assertions.assertThat(result.ruleResults()).isEmpty();
        Assertions.assertThat(result.matched()).isFalse();
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
    }

    @Test
    void collectionsAreDefensivelyCopiedAndUnmodifiable() {
        List<RuleResult> input = new ArrayList<>();
        input.add(new RuleResult("r1", AuditType.MATCHED, "ok"));
        EvaluationResult result = new EvaluationResult(input);
        Assertions.assertThat(result.ruleResults()).hasSize(1);

        input.add(new RuleResult("r2", AuditType.MATCHED, "ok"));
        Assertions.assertThat(result.ruleResults()).hasSize(1);

        Assertions.assertThatThrownBy(() -> result.ruleResults().add(new RuleResult("r3", AuditType.MATCHED, "ok")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
