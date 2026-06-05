package org.hubbers.react;

import org.hubbers.react.tool.ApprovalHandler;

import java.util.Scanner;

/**
 * Console-based {@link ApprovalHandler} that blocks on stdin waiting for explicit user confirmation
 * before allowing a tool with {@code approval="required"} to execute (human-in-the-loop).
 */
public class MinimalApprovalHandler implements ApprovalHandler {

    private final Scanner scanner;

    public MinimalApprovalHandler(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public boolean approve(String toolName, String callToken) {
        System.out.println("\n⚠️  [APPROVAZIONE RICHIESTA] Il tool '" + toolName + "' richiede conferma.");
        System.out.println("   Chiamata: " + callToken.trim());
        System.out.print("   Procedere? (s/n): ");
        String answer = scanner.nextLine().trim().toLowerCase();
        return answer.equals("s") || answer.equals("si") || answer.equals("y") || answer.equals("yes");
    }
}
