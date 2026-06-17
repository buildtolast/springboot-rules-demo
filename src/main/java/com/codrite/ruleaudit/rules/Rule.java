package com.codrite.ruleaudit.rules;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Persistent entity representing a business rule.
 * <p>
 * Rules are stored in MongoDB and contain SpEL expressions that are 
 * evaluated against incoming Kafka records.
 */
@Document("rule")
public class Rule {

    @Id
    private String id;

    /**
     * Human-readable description of what the rule checks.
     */
    private String description;

    /**
     * The Spring Expression Language (SpEL) logic for the rule.
     * Example: "['amount'] > 1000"
     */
    private String spelExpression;

    /**
     * Flag to enable or disable the rule without deleting it.
     */
    private boolean active;

    /**
     * Last time the rule was modified.
     */
    private Instant updatedAt;

    public Rule() {
    }

    public Rule(String description, String spelExpression, boolean active) {
        this.description = description;
        this.spelExpression = spelExpression;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSpelExpression() {
        return spelExpression;
    }

    public void setSpelExpression(String spelExpression) {
        this.spelExpression = spelExpression;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
