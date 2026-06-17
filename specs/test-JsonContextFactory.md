# GENERATE: src/test/java/com/codrite/ruleaudit/json/JsonContextFactoryTest.java

JUnit 5 + AssertJ. Use assertThatThrownBy for exceptions. Class:
JsonContextFactoryTest in package com.codrite.ruleaudit.json.

Subject: new JsonContextFactory() (default constructor). Cover one @Test each:
1. toRoot(String) of a flat object {"amount":5000,"region":"EU"} returns a Map
   with entry amount -> 5000 (a Number) and region -> "EU".
2. toRoot(String) of a nested object {"order":{"total":50}} returns a Map whose
   "order" value is itself a Map containing total -> 50 (so SpEL ['order']['total'] works).
3. toRoot(String) of an object containing an array {"tags":["a","b"]} returns a
   List for "tags". Cast it to java.util.List<Object> (NOT List<?> - a wildcard
   breaks AssertJ containsExactly), then assert containsExactly("a","b").
4. Malformed JSON (e.g. the string "{not json") throws JsonParseException.
5. Valid JSON whose top level is NOT an object (e.g. "[1,2,3]" and "42") throws
   JsonParseException.
6. toRoot(byte[]) returns the same Map as toRoot(String) for identical UTF-8 bytes.

Output exactly one fenced java code block: the complete test file.
