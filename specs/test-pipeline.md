# GENERATE: src/test/java/com/codrite/ruleaudit/RuleMatchingPipelineIT.java

Integration test (no mocks) wiring the real components together:
JsonContextFactory -> CompiledRule.compile -> RuleEvaluator. JUnit 5 + AssertJ.
Class: RuleMatchingPipelineIT in package com.codrite.ruleaudit.
Imports: json.JsonContextFactory, rules.CompiledRule, eval.RuleEvaluator,
eval.EvaluationResult, audit.AuditType.

Set up real instances (no Mockito): a JsonContextFactory, a RuleEvaluator, and a
fixed rule set:
  r1 = CompiledRule.compile("r1","high amount","['amount'] > 1000")
  r2 = CompiledRule.compile("r2","eu region","['region'] == 'EU'")

One @Test per behavior, feeding raw JSON event strings through the whole pipeline
(parse -> evaluate):
1. Event {"amount":5000,"region":"US"} matches via r1 only ->
   verdict()==MATCHED, matchedRuleIds contains "r1" and not "r2".
2. Event {"amount":50,"region":"EU"} matches via r2 only ->
   verdict()==MATCHED, matchedRuleIds contains "r2".
3. Event {"amount":50,"region":"US"} matches nothing ->
   verdict()==UNMATCHED, matchedRuleIds empty.
4. Malformed event "{bad json" -> JsonContextFactory.toRoot throws
   JsonParseException (assertThatThrownBy), demonstrating parse failures surface
   before evaluation (the topology will map this to an ERRORED audit).

Output exactly one fenced java code block: the complete test file.
