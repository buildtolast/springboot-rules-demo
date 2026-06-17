# SHARED API CONTRACT 2 — pipeline slice (com.codrite.ruleaudit)

Spring Boot 3.3 / Java 21. Generate ONE Java file. Use these EXACT signatures of
sibling classes; do NOT redefine them. Output exactly one fenced ```java block.

## Existing core (already implemented, package com.codrite.ruleaudit.*)
```java
// audit
public enum AuditType { MATCHED, UNMATCHED, ERRORED }
// eval
public record EvaluationResult(java.util.List<String> matchedRuleIds,
    java.util.List<String> evaluatedRuleIds, java.util.Map<String,String> errors) {
    public boolean matched(); public AuditType verdict();
}
public class RuleEvaluator {
    public EvaluationResult evaluate(java.util.Map<String,Object> root,
        java.util.List<com.codrite.ruleaudit.rules.CompiledRule> rules);
}
// rules
public record CompiledRule(String id, String description,
    org.springframework.expression.Expression expression) {
    public static CompiledRule compile(String id, String description, String spel);
}
// json
public class JsonParseException extends RuntimeException { /* (String,Throwable) */ }
public class JsonContextFactory {
    public java.util.Map<String,Object> toRoot(String json);   // throws JsonParseException
    public java.util.Map<String,Object> toRoot(byte[] json);
}
```

## New classes in THIS slice (their final signatures)
```java
// package com.codrite.ruleaudit.rules
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "rule")
public class Rule {
    @jakarta.persistence.Id @jakarta.persistence.GeneratedValue(...) Long id;
    String description; String spelExpression; boolean active; java.time.Instant updatedAt;
    // no-arg constructor + getters/setters
}
public interface RuleRepository extends org.springframework.data.jpa.repository.JpaRepository<Rule,Long> {
    java.util.List<Rule> findByActiveTrue();
}
public class RuleCache {                 // thread-safe holder of compiled rules
    public java.util.List<CompiledRule> get();
    public void replace(java.util.List<CompiledRule> rules);
}
@org.springframework.stereotype.Component
public class RuleLoader {
    public RuleLoader(RuleRepository repository, RuleCache cache);
    public int reload();                 // load active rules, compile, populate cache; return count
}

// package com.codrite.ruleaudit.audit
public final class AuditKey {
    public static String of(String topic, int partition, long offset);  // sha256 hex
}
@org.springframework.data.mongodb.core.mapping.Document("audits")
public record AuditRecord(
    @org.springframework.data.annotation.Id String auditId,
    int schemaVersion, AuditType auditType,
    java.util.List<String> matchedRuleIds, java.util.List<String> evaluatedRuleIds,
    java.util.Map<String,String> errors,
    String sourceEvent, String routedEvent,
    String sourceTopic, int partition, long offset, java.time.Instant timestamp) {}

// package com.codrite.ruleaudit.topology
// org.apache.kafka.streams.processor.api.Processor<String,String,String,String>
public class RoutingProcessor implements
        org.apache.kafka.streams.processor.api.Processor<String,String,String,String> {
    public RoutingProcessor(RuleEvaluator evaluator, RuleCache cache,
        JsonContextFactory jsonFactory, com.fasterxml.jackson.databind.ObjectMapper mapper);
}

// package com.codrite.ruleaudit.demo
public final class DemoMessages {
    public static java.util.List<String> generate(int count);  // JSON event strings
}
```
