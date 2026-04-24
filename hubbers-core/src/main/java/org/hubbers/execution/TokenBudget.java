package org.hubbers.execution;

/**
 * Defines a token budget for a single execution run.
 *
 * <p>When set on an agent execution, the framework will halt further LLM calls
 * once the total token count exceeds the configured maximum.</p>
 *
 * @param maxTotalTokens maximum total tokens (prompt + completion) allowed
 * @param warnAtPercentage percentage at which to log a warning (0-100)
 */
public record TokenBudget(long maxTotalTokens, int warnAtPercentage) {

    /** Default budget: 100k tokens, warn at 80%. */
    public static final TokenBudget DEFAULT = new TokenBudget(100_000, 80);

    /**
     * Check whether the given token count exceeds this budget.
     *
     * @param currentTokens current total token count
     * @return true if the budget is exceeded
     */
    public boolean isExceeded(long currentTokens) {
        return currentTokens >= maxTotalTokens;
    }

    /**
     * Check whether the given token count has crossed the warning threshold.
     *
     * @param currentTokens current total token count
     * @return true if past the warning threshold but not yet exceeded
     */
    public boolean isWarning(long currentTokens) {
        long threshold = (maxTotalTokens * warnAtPercentage) / 100;
        return currentTokens >= threshold && currentTokens < maxTotalTokens;
    }
}
