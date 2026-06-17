# GENERATE: src/main/java/com/codrite/ruleaudit/rules/RuleCache.java

Thread-safe holder of compiled rules per the contract. Package
com.codrite.ruleaudit.rules.
- Annotate with @org.springframework.stereotype.Component.
- private final java.util.concurrent.atomic.AtomicReference<java.util.List<CompiledRule>>
  ref initialized to an empty unmodifiable list (List.of()).
- public java.util.List<CompiledRule> get() returns ref.get().
- public void replace(java.util.List<CompiledRule> rules): store
  List.copyOf(rules) (null -> empty) via ref.set(...).
- Output exactly one top-level class.
