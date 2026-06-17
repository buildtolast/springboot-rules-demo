package com.codrite.ruleaudit.rules;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import java.util.List;

/**
 * Repository interface for {@link Rule} entities stored in MongoDB.
 * Using ReactiveMongoRepository for non-blocking I/O.
 */
public interface RuleRepository extends ReactiveMongoRepository<Rule, String> {
    /**
     * Finds all rules currently marked as active.
     * @return Flux of active rules.
     */
    Flux<Rule> findByActiveTrue();
}
