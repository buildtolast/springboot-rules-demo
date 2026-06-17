# GENERATE: src/main/java/com/codrite/ruleaudit/rules/CompiledRule.java

Implement the CompiledRule record EXACTLY as in the contract above.
Requirements:
- Package com.codrite.ruleaudit.rules.
- A single shared, thread-safe parser: private static final
  org.springframework.expression.spel.standard.SpelExpressionParser PARSER =
  new SpelExpressionParser();
- static CompiledRule compile(String id, String description, String spel):
  returns new CompiledRule(id, description, PARSER.parseExpression(spel)).
  Let SpelParseException propagate on bad syntax (do not catch it).
- expression component type is org.springframework.expression.Expression.
