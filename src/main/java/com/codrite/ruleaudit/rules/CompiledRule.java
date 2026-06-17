package com.codrite.ruleaudit.rules;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * An immutable, pre-compiled business rule.
 * <p>
 * This record stores the executable {@link Expression} resulting from parsing 
 * a SpEL string. Pre-parsing allows for high-performance evaluation during 
 * stream processing.
 * 
 * @param id          Unique rule identifier.
 * @param description Human-readable description.
 * @param expression  The parsed, executable SpEL expression.
 */
public record CompiledRule(String id, String description, Expression expression) {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Factory method to parse and compile a SpEL string into a {@link CompiledRule}.
     * 
     * @param id          The rule ID.
     * @param description The rule description.
     * @param spel        The raw SpEL expression string.
     * @return A new CompiledRule instance.
     * @throws org.springframework.expression.spel.SpelParseException if the expression is invalid.
     */
    public static CompiledRule compile(String id, String description, String spel) {
        return new CompiledRule(id, description, PARSER.parseExpression(spel));
    }
}
