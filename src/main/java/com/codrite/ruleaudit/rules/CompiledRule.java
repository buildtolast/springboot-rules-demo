package com.codrite.ruleaudit.rules;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public record CompiledRule(String id, String description, Expression expression) {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    public static CompiledRule compile(String id, String description, String spel) {
        return new CompiledRule(id, description, PARSER.parseExpression(spel));
    }
}
