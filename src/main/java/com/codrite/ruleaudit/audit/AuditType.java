package com.codrite.ruleaudit.audit;

/**
 * Categorization of an evaluation result.
 */
public enum AuditType {
    /** At least one active rule matched the record payload. */
    MATCHED,
    /** No rules matched the payload. */
    UNMATCHED,
    /** An error occurred during payload parsing or rule evaluation. */
    ERRORED
}
