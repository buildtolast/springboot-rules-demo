package com.codrite.ruleaudit.audit;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for {@link AuditRecord} entities.
 * Provides asynchronous access to the 'audits' collection in MongoDB.
 */
@Repository
public interface AuditRepository extends ReactiveMongoRepository<AuditRecord, String> {
}
