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
            new String[]{"Security alert for session access from US (Rule 1)", "['amount'] > 43618 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 5 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 678].size() > 0"},
            new String[]{"System transfer verification for APAC channel (Rule 2)", "['amount'] > 38620 and ['region'] == 'APAC' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.16"},
            new String[]{"System customer verification for LATAM channel (Rule 3)", "['amount'] > 18508 and ['region'] == 'LATAM' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 5 and ['tier'] == 'premium' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"System account verification for EU channel (Rule 4)", "['amount'] > 40598 and ['region'] == 'EU' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 4 and ['tier'] == 'premium' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"System account verification for EMEA channel (Rule 5)", "['amount'] > 6780 and ['region'] == 'EMEA' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 2 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value transfer US audit (Rule 6)", "['amount'] > 44670 and ['region'] == 'US' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Transaction monitor: transfer type for LATAM tier (Rule 7)", "['amount'] > 14640 and ['region'] == 'LATAM' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 5 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 667].size() > 0"},
            new String[]{"Transaction monitor: payment type for EMEA tier (Rule 8)", "['amount'] > 3654 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.09"},
            new String[]{"System customer verification for APAC channel (Rule 9)", "['amount'] > 29568 and ['region'] == 'APAC' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 1 and ['tier'] == 'premium' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Compliance check for payment in EMEA region (Rule 10)", "['amount'] > 25127 and ['region'] == 'EMEA' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 1 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.13"},
            new String[]{"Compliance check for transfer in APAC region (Rule 11)", "['amount'] > 44816 and ['region'] == 'APAC' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 678].size() > 0"},
            new String[]{"Compliance check for user in LATAM region (Rule 12)", "['amount'] > 25595 and ['region'] == 'LATAM' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 1 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 272].size() > 0"},
            new String[]{"Transaction monitor: session type for US tier (Rule 13)", "['amount'] > 22973 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 2 and ['tier'] == 'premium' and ['order'] != null and ['order']['items'].?[['price'] > 142].size() > 0"},
            new String[]{"System user verification for EMEA channel (Rule 14)", "['amount'] > 24146 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for user access from EMEA (Rule 15)", "['amount'] > 5042 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.15"},
            new String[]{"Compliance check for account in EMEA region (Rule 16)", "['amount'] > 7122 and ['region'] == 'EMEA' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value invoice EU audit (Rule 17)", "['amount'] > 42965 and ['region'] == 'EU' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 4 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.06"},
            new String[]{"System order verification for EMEA channel (Rule 18)", "['amount'] > 1493 and ['region'] == 'EMEA' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 397].size() > 0"},
            new String[]{"Security alert for customer access from US (Rule 19)", "['amount'] > 9245 and ['region'] == 'US' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Transaction monitor: account type for EMEA tier (Rule 20)", "['amount'] > 37249 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for customer access from US (Rule 21)", "['amount'] > 28554 and ['region'] == 'US' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 1 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.2"},
            new String[]{"High value order APAC audit (Rule 22)", "['amount'] > 2700 and ['region'] == 'APAC' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 1 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.2"},
            new String[]{"Transaction monitor: payment type for LATAM tier (Rule 23)", "['amount'] > 49054 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 4 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 841].size() > 0"},
            new String[]{"High value order EMEA audit (Rule 24)", "['amount'] > 29519 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 2 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Transaction monitor: payment type for LATAM tier (Rule 25)", "['amount'] > 3708 and ['region'] == 'LATAM' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 1 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value payment LATAM audit (Rule 26)", "['amount'] > 37310 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value user US audit (Rule 27)", "['amount'] > 23378 and ['region'] == 'US' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 107].size() > 0"},
            new String[]{"System transfer verification for APAC channel (Rule 28)", "['amount'] > 19673 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.18"},
            new String[]{"Security alert for customer access from APAC (Rule 29)", "['amount'] > 44216 and ['region'] == 'APAC' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.18"},
            new String[]{"High value order APAC audit (Rule 30)", "['amount'] > 12488 and ['region'] == 'APAC' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.14"},
            new String[]{"System transfer verification for APAC channel (Rule 31)", "['amount'] > 7030 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 4 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 503].size() > 0"},
            new String[]{"High value session EMEA audit (Rule 32)", "['amount'] > 5235 and ['region'] == 'EMEA' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 1 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value account EU audit (Rule 33)", "['amount'] > 38545 and ['region'] == 'EU' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 4 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 792].size() > 0"},
            new String[]{"High value account US audit (Rule 34)", "['amount'] > 49224 and ['region'] == 'US' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 4 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.05"},
            new String[]{"High value transfer LATAM audit (Rule 35)", "['amount'] > 17308 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.17"},
            new String[]{"Compliance check for invoice in APAC region (Rule 36)", "['amount'] > 26323 and ['region'] == 'APAC' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.12"},
            new String[]{"Compliance check for transfer in EU region (Rule 37)", "['amount'] > 40339 and ['region'] == 'EU' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 4 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.21"},
            new String[]{"System customer verification for EMEA channel (Rule 38)", "['amount'] > 45405 and ['region'] == 'EMEA' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 2 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 294].size() > 0"},
            new String[]{"Security alert for order access from APAC (Rule 39)", "['amount'] > 44066 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.13"},
            new String[]{"Compliance check for session in EMEA region (Rule 40)", "['amount'] > 17344 and ['region'] == 'EMEA' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 1 and ['tier'] == 'premium' and ['order'] != null and ['order']['items'].?[['price'] > 196].size() > 0"},
            new String[]{"High value order US audit (Rule 41)", "['amount'] > 48911 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Compliance check for transfer in US region (Rule 42)", "['amount'] > 44641 and ['region'] == 'US' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 1 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for transfer access from EMEA (Rule 43)", "['amount'] > 29072 and ['region'] == 'EMEA' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 2 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Compliance check for payment in EU region (Rule 44)", "['amount'] > 39850 and ['region'] == 'EU' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value account LATAM audit (Rule 45)", "['amount'] > 41981 and ['region'] == 'LATAM' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 4 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value order EU audit (Rule 46)", "['amount'] > 31393 and ['region'] == 'EU' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 202].size() > 0"},
            new String[]{"High value order APAC audit (Rule 47)", "['amount'] > 14312 and ['region'] == 'APAC' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 2 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.21"},
            new String[]{"Security alert for session access from APAC (Rule 48)", "['amount'] > 47538 and ['region'] == 'APAC' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 237].size() > 0"},
            new String[]{"Security alert for user access from US (Rule 49)", "['amount'] > 32599 and ['region'] == 'US' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.24"},
            new String[]{"High value session US audit (Rule 50)", "['amount'] > 4100 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 3 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Compliance check for order in US region (Rule 51)", "['amount'] > 6677 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 2 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.08"},
            new String[]{"High value payment LATAM audit (Rule 52)", "['amount'] > 21693 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 2 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.16"},
            new String[]{"High value invoice APAC audit (Rule 53)", "['amount'] > 11577 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 161].size() > 0"},
            new String[]{"Compliance check for payment in EU region (Rule 54)", "['amount'] > 2460 and ['region'] == 'EU' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.16"},
            new String[]{"Security alert for payment access from US (Rule 55)", "['amount'] > 35263 and ['region'] == 'US' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.11"},
            new String[]{"Compliance check for user in EMEA region (Rule 56)", "['amount'] > 25176 and ['region'] == 'EMEA' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 2 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.16"},
            new String[]{"High value invoice EU audit (Rule 57)", "['amount'] > 20854 and ['region'] == 'EU' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 2 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 105].size() > 0"},
            new String[]{"High value transfer LATAM audit (Rule 58)", "['amount'] > 3102 and ['region'] == 'LATAM' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 3 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Transaction monitor: payment type for US tier (Rule 59)", "['amount'] > 36552 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 5 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 487].size() > 0"},
            new String[]{"High value user LATAM audit (Rule 60)", "['amount'] > 1325 and ['region'] == 'LATAM' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'premium' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for customer access from LATAM (Rule 61)", "['amount'] > 27274 and ['region'] == 'LATAM' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 1 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.1"},
            new String[]{"Transaction monitor: customer type for US tier (Rule 62)", "['amount'] > 4137 and ['region'] == 'US' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value transfer LATAM audit (Rule 63)", "['amount'] > 11653 and ['region'] == 'LATAM' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 2 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.11"},
            new String[]{"High value transfer APAC audit (Rule 64)", "['amount'] > 4896 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 732].size() > 0"},
            new String[]{"System transfer verification for LATAM channel (Rule 65)", "['amount'] > 20673 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 4 and ['tier'] == 'standard' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"System invoice verification for EU channel (Rule 66)", "['amount'] > 32202 and ['region'] == 'EU' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 5 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 319].size() > 0"},
            new String[]{"Security alert for payment access from LATAM (Rule 67)", "['amount'] > 30701 and ['region'] == 'LATAM' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 2 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 803].size() > 0"},
            new String[]{"High value order APAC audit (Rule 68)", "['amount'] > 42285 and ['region'] == 'APAC' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 5 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 926].size() > 0"},
            new String[]{"Security alert for user access from EU (Rule 69)", "['amount'] > 9722 and ['region'] == 'EU' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['order'] != null and ['order']['items'].?[['price'] > 539].size() > 0"},
            new String[]{"System session verification for US channel (Rule 70)", "['amount'] > 14752 and ['region'] == 'US' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'premium' and ['metadata']['tax_rate'] >= 0.2"},
            new String[]{"Compliance check for session in US region (Rule 71)", "['amount'] > 44965 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.21"},
            new String[]{"Transaction monitor: payment type for US tier (Rule 72)", "['amount'] > 6479 and ['region'] == 'US' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 1 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Transaction monitor: user type for US tier (Rule 73)", "['amount'] > 7110 and ['region'] == 'US' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 1 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 550].size() > 0"},
            new String[]{"High value order US audit (Rule 74)", "['amount'] > 8370 and ['region'] == 'US' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 3 and ['tier'] == 'standard' and ['metadata']['tax_rate'] >= 0.11"},
            new String[]{"Transaction monitor: invoice type for EMEA tier (Rule 75)", "['amount'] > 24126 and ['region'] == 'EMEA' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 5 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.15"},
            new String[]{"High value session EU audit (Rule 76)", "['amount'] > 15196 and ['region'] == 'EU' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 4 and ['tier'] == 'vip' and ['order'] != null and ['order']['items'].?[['price'] > 663].size() > 0"},
            new String[]{"System order verification for LATAM channel (Rule 77)", "['amount'] > 15773 and ['region'] == 'LATAM' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 3 and ['tier'] == 'premium' and ['order'] != null and ['order']['items'].?[['price'] > 966].size() > 0"},
            new String[]{"Security alert for customer access from APAC (Rule 78)", "['amount'] > 5016 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 2 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value transfer EMEA audit (Rule 79)", "['amount'] > 23576 and ['region'] == 'EMEA' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.09"},
            new String[]{"Compliance check for payment in EU region (Rule 80)", "['amount'] > 46750 and ['region'] == 'EU' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 306].size() > 0"},
            new String[]{"High value invoice EU audit (Rule 81)", "['amount'] > 13460 and ['region'] == 'EU' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 1 and ['tier'] == 'vip' and ['metadata']['tax_rate'] >= 0.19"},
            new String[]{"Security alert for account access from APAC (Rule 82)", "['amount'] > 15495 and ['region'] == 'APAC' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'standard' and ['metadata']['tax_rate'] >= 0.15"},
            new String[]{"Compliance check for customer in EMEA region (Rule 83)", "['amount'] > 22690 and ['region'] == 'EMEA' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 5 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for user access from US (Rule 84)", "['amount'] > 16665 and ['region'] == 'US' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 3 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.18"},
            new String[]{"Security alert for user access from APAC (Rule 85)", "['amount'] > 30136 and ['region'] == 'APAC' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 4 and ['tier'] == 'vip' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for customer access from APAC (Rule 86)", "['amount'] > 23633 and ['region'] == 'APAC' and ['metadata']['source'] == 'api' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"System transfer verification for LATAM channel (Rule 87)", "['amount'] > 7864 and ['region'] == 'LATAM' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 5 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.12"},
            new String[]{"Security alert for customer access from APAC (Rule 88)", "['amount'] > 4036 and ['region'] == 'APAC' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 2 and ['tier'] == 'standard' and ['metadata']['tax_rate'] >= 0.19"},
            new String[]{"Security alert for customer access from EMEA (Rule 89)", "['amount'] > 46075 and ['region'] == 'EMEA' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 2 and ['tier'] == 'standard' and ['order'] != null and ['order']['items'].?[['price'] > 775].size() > 0"},
            new String[]{"Security alert for invoice access from US (Rule 90)", "['amount'] > 3201 and ['region'] == 'US' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 1 and ['tier'] == 'gold' and ['order'] != null and ['order']['items'].?[['price'] > 501].size() > 0"},
            new String[]{"High value invoice APAC audit (Rule 91)", "['amount'] > 14486 and ['region'] == 'APAC' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 2 and ['tier'] == 'basic' and ['metadata']['tax_rate'] >= 0.09"},
            new String[]{"System order verification for LATAM channel (Rule 92)", "['amount'] > 18308 and ['region'] == 'LATAM' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 5 and ['tier'] == 'premium' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value account LATAM audit (Rule 93)", "['amount'] > 9950 and ['region'] == 'LATAM' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 5 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value order APAC audit (Rule 94)", "['amount'] > 35316 and ['region'] == 'APAC' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 3 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"System user verification for EU channel (Rule 95)", "['amount'] > 47395 and ['region'] == 'EU' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 1 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"Security alert for customer access from EU (Rule 96)", "['amount'] > 7246 and ['region'] == 'EU' and ['metadata']['source'] == 'legacy' and ['metadata']['priority'] <= 3 and ['tier'] == 'basic' and ['timestamp'] != null and ['timestamp'].startsWith('202')"},
            new String[]{"High value session LATAM audit (Rule 97)", "['amount'] > 13679 and ['region'] == 'LATAM' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 5 and ['tier'] == 'standard' and ['metadata']['tax_rate'] >= 0.13"},
            new String[]{"System invoice verification for US channel (Rule 98)", "['amount'] > 5323 and ['region'] == 'US' and ['metadata']['source'] == 'partner' and ['metadata']['priority'] <= 5 and ['tier'] == 'premium' and ['order'] != null and ['order']['items'].?[['price'] > 862].size() > 0"},
            new String[]{"High value session EMEA audit (Rule 99)", "['amount'] > 24530 and ['region'] == 'EMEA' and ['metadata']['source'] == 'mobile' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['metadata']['tax_rate'] >= 0.18"},
            new String[]{"System customer verification for EMEA channel (Rule 100)", "['amount'] > 5234 and ['region'] == 'EMEA' and ['metadata']['source'] == 'web' and ['metadata']['priority'] <= 4 and ['tier'] == 'gold' and ['timestamp'] != null and ['timestamp'].startsWith('202')"}
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
