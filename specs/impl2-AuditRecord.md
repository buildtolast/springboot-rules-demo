# GENERATE: src/main/java/com/codrite/ruleaudit/audit/AuditRecord.java

Mongo document AND Kafka JSON DTO per the contract. Package
com.codrite.ruleaudit.audit. It is a record with these components in this order:
  String auditId, int schemaVersion, AuditType auditType,
  java.util.List<String> matchedRuleIds, java.util.List<String> evaluatedRuleIds,
  java.util.Map<String,String> errors, String sourceEvent, String routedEvent,
  String sourceTopic, int partition, long offset, java.time.Instant timestamp

Annotations:
- Annotate the record type with
  @org.springframework.data.mongodb.core.mapping.Document(collection = "audits").
- Annotate the auditId component with @org.springframework.data.annotation.Id.

Do NOT add a compact constructor or any methods. Output exactly one top-level
record, fully importing the annotations and AuditType
(com.codrite.ruleaudit.audit.AuditType is the same package, no import needed).
