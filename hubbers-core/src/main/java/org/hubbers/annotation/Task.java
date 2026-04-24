package org.hubbers.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a pipeline step declaration inside a {@link Pipeline} class.
 *
 * <p>The method must return a {@code PipelineStep}. Steps are assembled in ascending
 * {@link #order()} sequence. Use {@code order} to guarantee a deterministic pipeline
 * regardless of JVM method-discovery order.</p>
 *
 * <pre>{@code
 * @Task(order = 1)
 * PipelineStep researchTask() {
 *     return PipelineStep.builder()
 *         .id("research_task")
 *         .agent("Senior Research Analyst")
 *         .input("query", "Latest AI frameworks")
 *         .build();
 * }
 *
 * @Task(order = 2)
 * PipelineStep reportTask() {
 *     return PipelineStep.builder()
 *         .id("report_task")
 *         .agent("Technical Content Strategist")
 *         .input("content", "${steps.research_task.output}")
 *         .build();
 * }
 * }</pre>
 *
 * @see Pipeline
 * @see Agent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Task {

    /**
     * Execution order of this step within the pipeline. Must be unique per flow class.
     * Steps are sorted ascending before the pipeline is assembled.
     */
    int order();
}
