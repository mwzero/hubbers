package org.hubbers.react.tool;

/**
 * Human-in-the-loop callback invoked before executing a tool that carries
 * {@code approval="required"} in its call tag.
 *
 * <p>Implementations can block on stdin, show a UI dialog, call a webhook,
 * or apply any policy-based decision. The loop will skip tool execution when
 * this method returns {@code false}.</p>
 */
@FunctionalInterface
public interface ApprovalHandler {

    /**
     * Asks for human confirmation before executing a tool.
     *
     * @param toolName  the name of the tool about to be executed
     * @param callToken the full raw call token (including attributes and body)
     * @return {@code true} if the user approves the execution, {@code false} to cancel it
     */
    boolean approve(String toolName, String callToken);
}
