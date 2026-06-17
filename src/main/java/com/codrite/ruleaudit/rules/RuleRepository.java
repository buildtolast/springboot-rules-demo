package com.codrite.ruleaudit.rules;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface RuleRepository extends MongoRepository<Rule, String> {
    List<Rule> findByActiveTrue();
}
