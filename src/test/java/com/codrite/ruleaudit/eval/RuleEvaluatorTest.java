package com.codrite.ruleaudit.eval;

import com.codrite.ruleaudit.audit.AuditType;
import com.codrite.ruleaudit.rules.CompiledRule;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;

class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    @Test
    void singleRuleMatches() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults()).hasSize(1);
        Assertions.assertThat(result.ruleResults().get(0).ruleId()).isEqualTo("r1");
        Assertions.assertThat(result.ruleResults().get(0).type()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults().get(0).reason()).isNull();
    }

    @Test
    void singleRuleDoesNotMatch() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        Map<String, Object> root = Map.of("amount", 10);
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.ruleResults()).hasSize(1);
        Assertions.assertThat(result.ruleResults().get(0).ruleId()).isEqualTo("r1");
        Assertions.assertThat(result.ruleResults().get(0).type()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.ruleResults().get(0).reason())
                .contains("Condition not met")
                .contains("['amount'] > 1000")
                .contains("amount=10");
    }

    @Test
    void matchWins() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        CompiledRule r2 = CompiledRule.compile("r2", "Throw error", "['amount'].noSuchMethod()");
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of(r1, r2);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults()).extracting("ruleId").containsExactly("r1", "r2");
        Assertions.assertThat(result.ruleResults()).extracting("type").containsExactly(AuditType.MATCHED, AuditType.ERRORED);
    }

    @Test
    void erroredOnly() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        CompiledRule r2 = CompiledRule.compile("r2", "Throw error", "['amount'].noSuchMethod()");
        Map<String, Object> root = Map.of("amount", 10);
        List<CompiledRule> rules = List.of(r1, r2);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.ERRORED);
        Assertions.assertThat(result.ruleResults()).extracting("type").containsExactly(AuditType.UNMATCHED, AuditType.ERRORED);
    }

    @Test
    void emptyRuleList() {
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of();

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.ruleResults()).isEmpty();
    }

    @Test
    void spelInjectionBlocked() {
        CompiledRule r1 = CompiledRule.compile("r1", "Injection attempt", "T(java.lang.Runtime).getRuntime().exec('echo pwned') != null");
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isNotEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults().get(0).type()).isEqualTo(AuditType.ERRORED);
    }

    @Test
    void wrongTypedFieldDoesNotCrash() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        Map<String, Object> root = Map.of("amount", "not-a-number");
        List<CompiledRule> rules = List.of(r1);

        Assertions.assertThatCode(() -> evaluator.evaluate(root, rules)).doesNotThrowAnyException();

        var result = evaluator.evaluate(root, rules);
        Assertions.assertThat(result.verdict()).isNotEqualTo(AuditType.MATCHED);
    }

    @Test
    void complexNestedArrayMatch() {
        CompiledRule r1 = CompiledRule.compile("r1", "Price check", "['order']['items'].?[['price'] > 500].size() > 0");
        Map<String, Object> root = Map.of(
            "order", Map.of(
                "items", List.of(
                    Map.of("id", "it-1", "price", 600),
                    Map.of("id", "it-2", "price", 400)
                )
            )
        );
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults().get(0).type()).isEqualTo(AuditType.MATCHED);
    }

    @Test
    void complexNestedArrayNoMatch() {
        CompiledRule r1 = CompiledRule.compile("r1", "Price check", "['order']['items'].?[['price'] > 1000].size() > 0");
        Map<String, Object> root = Map.of(
            "order", Map.of(
                "items", List.of(
                    Map.of("id", "it-1", "price", 600),
                    Map.of("id", "it-2", "price", 400)
                )
            )
        );
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
    }

    @Test
    void testLatencyMeasurement() {
        CompiledRule r1 = CompiledRule.compile("r1", "True rule", "true");
        Map<String, Object> root = Map.of("field", "value");
        List<CompiledRule> rules = List.of(r1);

        // This test doesn't measure latency directly as it's done in RoutingProcessor,
        // but we verify RuleEvaluator still works with the same input.
        var result = evaluator.evaluate(root, rules);
        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
    }
}
