package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.rules.Rule;
import com.codrite.ruleaudit.rules.RuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<Rule> list() {
        log.debug("GET /api/rules - Listing all rules");
        return ruleService.getAllRules();
    }

    @GetMapping("/{id}")
    public Rule get(@PathVariable String id) {
        log.debug("GET /api/rules/{} - Getting rule", id);
        return ruleService.getRule(id);
    }

    @PostMapping
    public Rule create(@RequestBody Rule rule) {
        log.info("POST /api/rules - Creating new rule: {}", rule.getDescription());
        return ruleService.saveRule(rule);
    }

    @PutMapping("/{id}")
    public Rule update(@PathVariable String id, @RequestBody Rule rule) {
        log.info("PUT /api/rules/{} - Updating rule: {}", id, rule.getDescription());
        rule.setId(id);
        return ruleService.saveRule(rule);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        log.info("DELETE /api/rules/{} - Deleting rule", id);
        ruleService.deleteRule(id);
    }
}
