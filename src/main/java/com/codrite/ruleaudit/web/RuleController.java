package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.rules.Rule;
import com.codrite.ruleaudit.rules.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing business rules.
 * <p>
 * Provides the API endpoints used by the React frontend to list, create, 
 * update, and delete rules.
 */
@Slf4j
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * Lists all existing rules.
     * @return A list of all rules in the system.
     */
    @GetMapping
    public List<Rule> list() {
        log.debug("GET /api/rules - Listing all rules");
        return ruleService.getAllRules();
    }

    /**
     * Retrieves a single rule by its ID.
     * @param id The rule identifier.
     * @return The rule details.
     */
    @GetMapping("/{id}")
    public Rule get(@PathVariable String id) {
        log.debug("GET /api/rules/{} - Getting rule", id);
        return ruleService.getRule(id);
    }

    /**
     * Creates a new business rule.
     * @param rule The rule definition to create.
     * @return The created rule with its generated ID.
     */
    @PostMapping
    public Rule create(@RequestBody Rule rule) {
        log.info("POST /api/rules - Creating new rule: {}", rule.getDescription());
        return ruleService.saveRule(rule);
    }

    /**
     * Updates an existing business rule.
     * @param id   The ID of the rule to update.
     * @param rule The new rule definition.
     * @return The updated rule.
     */
    @PutMapping("/{id}")
    public Rule update(@PathVariable String id, @RequestBody Rule rule) {
        log.info("PUT /api/rules/{} - Updating rule: {}", id, rule.getDescription());
        rule.setId(id);
        return ruleService.saveRule(rule);
    }

    /**
     * Deletes a business rule from the system.
     * @param id The ID of the rule to delete.
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        log.info("DELETE /api/rules/{} - Deleting rule", id);
        ruleService.deleteRule(id);
    }
}
