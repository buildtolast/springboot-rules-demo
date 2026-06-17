package com.codrite.ruleaudit.audit;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@org.springframework.data.mongodb.core.mapping.Document(collection = "audits")
public record AuditRecord(
        @org.springframework.data.annotation.Id String auditId,
        int schemaVersion,
        AuditType auditType,
        java.util.List<String> matchedRuleIds,
        java.util.List<String> evaluatedRuleIds,
        java.util.Map<String,String> errors,
        String sourceEvent,
        String routedEvent,
        String sourceTopic,
        int partition,
        long offset,
        java.time.Instant timestamp) {}
