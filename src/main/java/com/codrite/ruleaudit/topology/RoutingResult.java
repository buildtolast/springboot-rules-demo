package com.codrite.ruleaudit.topology;

import java.util.List;

/**
 * A container for the result of a single record evaluation.
 * 
 * @param matched     True if at least one rule matched the record.
 * @param routedValue The original record value if matched, null otherwise.
 * @param auditJsons  A list of serialized {@link com.codrite.ruleaudit.audit.AuditRecord} 
 *                    containing evaluation details for each rule.
 */
public record RoutingResult(boolean matched, String routedValue, List<String> auditJsons) {}
