# SHARED API CONTRACT — SpEL core slice (com.codrite.ruleaudit)

You are generating ONE Java file for a Spring Boot 3.3 / Java 21 project. These
are the FINAL signatures of the sibling classes in this slice. Use them exactly.
Do NOT redefine sibling classes. Do NOT invent extra public methods. Do NOT add
Spring annotations unless the spec says so. Java 21, no Lombok.

## Package: com.codrite.ruleaudit.audit
```java
public enum AuditType { MATCHED, UNMATCHED, ERRORED }
```

## Package: com.codrite.ruleaudit.eval
```java
// Immutable result of evaluating all active rules against one event.
// Record components hold defensive, unmodifiable copies (null -> empty).
public record EvaluationResult(
        java.util.List<String> matchedRuleIds,
        java.util.List<String> evaluatedRuleIds,
        java.util.Map<String,String> errors) {   // errors: ruleId -> message
    // compact constructor copies each collection (null becomes empty, unmodifiable)
    public boolean matched();        // == !matchedRuleIds.isEmpty()
    public AuditType verdict();      // matched -> MATCHED;
                                     // else errors not empty -> ERRORED;
                                     // else -> UNMATCHED
}
```

```java
// Stateless evaluator. Evaluates every rule; one rule throwing never aborts the
// others (match-wins). Per-rule exceptions are caught and recorded in errors.
public class RuleEvaluator {
    // For each rule: evaluate expression against root using
    // SimpleEvaluationContext.forReadOnlyDataBinding().build() and
    // expression.getValue(context, root, Boolean.class).
    //   true            -> add id to matchedRuleIds
    //   false or null   -> not matched, no error
    //   throws          -> add id->message to errors (rule treated as non-match)
    // evaluatedRuleIds always lists every rule id attempted, in order.
    public EvaluationResult evaluate(java.util.Map<String,Object> root,
                                     java.util.List<com.codrite.ruleaudit.rules.CompiledRule> rules);
}
```

## Package: com.codrite.ruleaudit.rules
```java
// A rule whose SpEL expression is parsed once at construction.
public record CompiledRule(String id, String description,
                           org.springframework.expression.Expression expression) {
    // Parses spel with a shared thread-safe SpelExpressionParser.
    // Propagates org.springframework.expression.spel.SpelParseException on bad syntax.
    public static CompiledRule compile(String id, String description, String spel);
}
```

## Package: com.codrite.ruleaudit.json
```java
// Unchecked. Thrown when a source event cannot be turned into a Map root.
public class JsonParseException extends RuntimeException {
    public JsonParseException(String message, Throwable cause);
}
```

```java
// Converts a JSON object event into a Map<String,Object> root for SpEL.
// Nested objects become nested Maps; arrays become Lists.
public class JsonContextFactory {
    public JsonContextFactory();                       // uses a new ObjectMapper
    public JsonContextFactory(com.fasterxml.jackson.databind.ObjectMapper mapper);
    // Malformed JSON, or valid JSON whose top level is NOT an object,
    // throws JsonParseException (never lets a Jackson exception escape).
    public java.util.Map<String,Object> toRoot(String json);
    public java.util.Map<String,Object> toRoot(byte[] json);
}
```

## SpEL rules for rule authors (relevant to tests)
- Root is a Map, so fields are accessed with the indexer: `['amount'] > 1000`.
- Nested: `['order']['total'] >= 50`.
- Security: SimpleEvaluationContext blocks type refs, so a malicious rule like
  `T(java.lang.Runtime).getRuntime().exec('x')` throws (recorded as an error),
  never executes.

## Output rules
- Output EXACTLY ONE fenced ```java code block: the complete file, with package
  declaration and all imports. Nothing outside the block.
