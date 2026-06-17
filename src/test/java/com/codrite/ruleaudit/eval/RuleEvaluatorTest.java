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
        Assertions.assertThat(result.matchedRuleIds()).containsExactly("r1");
        Assertions.assertThat(result.evaluatedRuleIds()).containsExactly("r1");
        Assertions.assertThat(result.errors()).isEmpty();
    }

    @Test
    void singleRuleDoesNotMatch() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        Map<String, Object> root = Map.of("amount", 10);
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.matchedRuleIds()).isEmpty();
        Assertions.assertThat(result.evaluatedRuleIds()).containsExactly("r1");
        Assertions.assertThat(result.errors()).isEmpty();
    }

    @Test
    void matchWins() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        CompiledRule r2 = CompiledRule.compile("r2", "Throw error", "['amount'].noSuchMethod()");
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of(r1, r2);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.matchedRuleIds()).containsExactly("r1");
        Assertions.assertThat(result.evaluatedRuleIds()).containsExactly("r1", "r2");
        Assertions.assertThat(result.errors()).containsKey("r2");
    }

    @Test
    void erroredOnly() {
        CompiledRule r1 = CompiledRule.compile("r1", "Amount > 1000", "['amount'] > 1000");
        CompiledRule r2 = CompiledRule.compile("r2", "Throw error", "['amount'].noSuchMethod()");
        Map<String, Object> root = Map.of("amount", 10);
        List<CompiledRule> rules = List.of(r1, r2);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.ERRORED);
        Assertions.assertThat(result.matchedRuleIds()).isEmpty();
        Assertions.assertThat(result.evaluatedRuleIds()).containsExactly("r1", "r2");
        Assertions.assertThat(result.errors()).containsKey("r2");
    }

    @Test
    void emptyRuleList() {
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of();

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.matchedRuleIds()).isEmpty();
        Assertions.assertThat(result.evaluatedRuleIds()).isEmpty();
        Assertions.assertThat(result.errors()).isEmpty();
    }

    @Test
    void spelInjectionBlocked() {
        CompiledRule r1 = CompiledRule.compile("r1", "Injection attempt", "T(java.lang.Runtime).getRuntime().exec('echo pwned') != null");
        Map<String, Object> root = Map.of("amount", 5000);
        List<CompiledRule> rules = List.of(r1);

        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isNotEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.errors()).containsKey("r1");
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
}
