package org.hubbers.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an agent declaration inside a {@link Pipeline} class.
 *
 * <p>The method must return an {@code AgentManifest}. {@link FlowRunner} will invoke
 * it at flow execution time and register the returned manifest in the artifact
 * repository so that pipeline steps can reference it by name.</p>
 *
 * <pre>{@code
 * @Agent
 * AgentManifest researcher() {
 *     return AgentManifest.builder()
 *         .name("Senior Research Analyst")
 *         .instructions("Find 3 key AI innovations.")
 *         .tools("serper.search", "file.read")
 *         .build();
 * }
 * }</pre>
 *
 * @see Pipeline
 * @see Task
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Agent {
}
