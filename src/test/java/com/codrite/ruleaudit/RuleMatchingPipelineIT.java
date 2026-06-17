package com.codrite.ruleaudit;

import com.codrite.ruleaudit.audit.AuditType;
import com.codrite.ruleaudit.eval.EvaluationResult;
import com.codrite.ruleaudit.eval.RuleEvaluator;
import com.codrite.ruleaudit.json.JsonContextFactory;
import com.codrite.ruleaudit.json.JsonParseException;
import com.codrite.ruleaudit.rules.CompiledRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import java.util.List;

public class RuleMatchingPipelineIT {

    private JsonContextFactory factory;
    private RuleEvaluator evaluator;
    private List<CompiledRule> rules;

    @BeforeEach
    void setup() {
        factory = new JsonContextFactory();
        evaluator = new RuleEvaluator();
        rules = List.of(
                CompiledRule.compile("r1", "high amount", "['amount'] > 1000"),
                CompiledRule.compile("r2", "eu region", "['region'] == 'EU'")
        );
    }

    @Test
    void testMatchOnlyR1() {
        String json = "{\"type\":\"order\",\"amount\":5000,\"region\":\"US\"}";
        var root = factory.toRoot(json);
        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults())
                .filteredOn(r -> r.type() == AuditType.MATCHED)
                .extracting("ruleId")
                .containsExactly("r1");
    }

    @Test
    void testMatchOnlyR2() {
        String json = "{\"type\":\"order\",\"amount\":50,\"region\":\"EU\"}";
        var root = factory.toRoot(json);
        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.MATCHED);
        Assertions.assertThat(result.ruleResults())
                .filteredOn(r -> r.type() == AuditType.MATCHED)
                .extracting("ruleId")
                .containsExactly("r2");
    }

    @Test
    void testNoMatch() {
        String json = "{\"amount\":50,\"region\":\"US\"}";
        var root = factory.toRoot(json);
        var result = evaluator.evaluate(root, rules);

        Assertions.assertThat(result.verdict()).isEqualTo(AuditType.UNMATCHED);
        Assertions.assertThat(result.ruleResults())
                .filteredOn(r -> r.type() == AuditType.MATCHED)
                .isEmpty();
    }

    @Test
    void testMalformedJsonThrows() {
        String json = "{bad json";
        Assertions.assertThatThrownBy(() -> factory.toRoot(json))
                .isInstanceOf(JsonParseException.class);
    }
}
