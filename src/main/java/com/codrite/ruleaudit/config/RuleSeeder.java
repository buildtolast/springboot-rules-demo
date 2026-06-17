package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.rules.Rule;
import com.codrite.ruleaudit.rules.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the database with a set of default rules on the first application startup.
 * <p>
 * This is particularly useful for demonstrations and testing environments 
 * to ensure there is immediate data to process.
 * <p>
 * Runs with {@code @Order(0)} to ensure rules are available before 
 * {@link PipelineStarter} attempts to load them.
 */
@Slf4j
@Component
@Order(0)
public class RuleSeeder implements ApplicationRunner {

    /**
     * Initial rule set definitions: [Description, SpEL Expression].
     */
    private static final List<String[]> RULES = List.of(
            new String[]{"amount over 1000", "['amount'] > 1000"},
            new String[]{"amount over 5000", "['amount'] > 5000"},
            new String[]{"amount at least 10000", "['amount'] >= 10000"},
            new String[]{"amount under 100", "['amount'] < 100"},
            new String[]{"region EU", "['region'] == 'EU'"},
            new String[]{"region US", "['region'] == 'US'"},
            new String[]{"region APAC", "['region'] == 'APAC'"},
            new String[]{"tier premium", "['tier'] == 'premium'"},
            new String[]{"tier standard", "['tier'] == 'standard'"},
            new String[]{"flagged true", "['flagged'] == true"},
            new String[]{"big EU", "['amount'] > 1000 and ['region'] == 'EU'"},
            new String[]{"premium high value", "['amount'] > 2000 and ['tier'] == 'premium'"},
            new String[]{"flagged EU", "['region'] == 'EU' and ['flagged'] == true"},
            new String[]{"mid amount", "['amount'] > 500 and ['amount'] < 5000"},
            new String[]{"premium or flagged", "['tier'] == 'premium' or ['flagged'] == true"},
            new String[]{"EU or APAC", "['region'] == 'EU' or ['region'] == 'APAC'"},
            new String[]{"flagged sizable", "['flagged'] == true and ['amount'] > 100"},
            new String[]{"standard US", "['tier'] == 'standard' and ['region'] == 'US'"},
            new String[]{"very big or premium", "['amount'] > 9000 or ['tier'] == 'premium'"},
            new String[]{"exactly 5000", "['amount'] == 5000"},
            new String[]{"premium EU", "['tier'] == 'premium' and ['region'] == 'EU'"},
            new String[]{"tiny unflagged", "['flagged'] == false and ['amount'] < 50"}
    );

    private final RuleService service;

    public RuleSeeder(RuleService service) {
        this.service = service;
    }

    /**
     * Executes the seeding logic.
     * @param args Application arguments.
     */
    @Override
    public void run(ApplicationArguments args) {
        // Check if database is empty to avoid re-seeding on every restart
        if (!service.getAllRules().isEmpty()) {
            log.info("Rule table already populated; skipping seed");
            return;
        }
        
        // Save all default rules as active
        for (String[] r : RULES) {
            service.saveRule(new Rule(r[0], r[1], true));
        }
        log.info("Seeded {} active rules", RULES.size());
    }
}
