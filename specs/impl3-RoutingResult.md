# GENERATE: src/main/java/com/codrite/ruleaudit/topology/RoutingResult.java

A small immutable carrier emitted by the evaluation processor and split by the
DSL into the target and audit topics. Package com.codrite.ruleaudit.topology.

public record RoutingResult(boolean matched, String routedValue, String auditJson) {}

- routedValue is the original event JSON (only used when matched); may be null.
- auditJson is the serialized AuditRecord (always present).
- No methods, no annotations. Output exactly one top-level record.
