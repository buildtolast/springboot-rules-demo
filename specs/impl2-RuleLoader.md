# GENERATE: src/main/java/com/codrite/ruleaudit/rules/RuleLoader.java

Loads active rules from the DB, compiles their SpEL, and populates the cache.
Package com.codrite.ruleaudit.rules.
- @org.springframework.stereotype.Component.
- Constructor injection: RuleLoader(RuleRepository repository, RuleCache cache).
- Use an SLF4J logger: private static final org.slf4j.Logger log =
  org.slf4j.LoggerFactory.getLogger(RuleLoader.class).
- public int reload():
    - List<Rule> rules = repository.findByActiveTrue();
    - Build List<CompiledRule> compiled = new ArrayList<>();
    - For each Rule r: try { compiled.add(CompiledRule.compile(String.valueOf(r.getId()),
      r.getDescription(), r.getSpelExpression())); }
      catch (RuntimeException e) { log.warn("Skipping un-parseable rule id={}: {}",
      r.getId(), e.getMessage()); }   // a bad rule must not abort the whole reload
    - cache.replace(compiled);
    - log.info("Loaded {} active rules into cache", compiled.size());
    - return compiled.size();
- Add @org.springframework.context.event.EventListener(
  org.springframework.boot.context.event.ApplicationReadyEvent.class) on a method
  onReady() that calls reload(), so the cache is populated at startup.
- Output exactly one top-level class.
