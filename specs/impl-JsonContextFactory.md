# GENERATE: src/main/java/com/codrite/ruleaudit/json/JsonContextFactory.java

Implement the JsonContextFactory class EXACTLY as in the contract above.
Output EXACTLY ONE top-level class: JsonContextFactory. Do NOT declare any other
class. JsonParseException ALREADY EXISTS in JsonParseException.java in this same
package (com.codrite.ruleaudit.json) - reference it directly (no import needed,
same package); do NOT re-declare it.
Requirements:
- Package com.codrite.ruleaudit.json.
- Field: private final com.fasterxml.jackson.databind.ObjectMapper mapper;
- Default constructor uses new ObjectMapper(); second constructor takes a mapper.
- toRoot(String json) and toRoot(byte[] json) both return Map<String,Object>:
  parse with mapper.readValue(json, new
  com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String,Object>>(){}).
  This naturally fails for malformed JSON AND for non-object top levels
  (arrays, numbers) because they cannot bind to a Map.
- Catch java.io.IOException and rethrow as new JsonParseException("Cannot parse
  event JSON into a Map root", e). Use IOException (NOT just JsonProcessingException)
  because the byte[] readValue overload declares throws IOException; JsonProcessingException
  is a subclass of IOException so this still covers malformed/non-object JSON.
  Let no Jackson/IO exception escape.
- Do not return null.
