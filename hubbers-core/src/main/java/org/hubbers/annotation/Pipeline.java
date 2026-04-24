package org.hubbers.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Hubbers pipeline definition.
 *
 * <p>Methods annotated with {@link Agent} declare agents that will be registered
 * in the artifact repository. Methods annotated with {@link Task} define the pipeline
 * steps, assembled in {@code order} sequence. No additional wiring is needed —
 * {@link FlowRunner} (or {@code RuntimeFacade.runFlow()}) handles everything.</p>
 *
 * <pre>{@code
 * @Pipeline("research-pipeline")
 * class ResearchFlow {
 *
 *     @Agent
 *     AgentManifest researcher() {
 *         return AgentManifest.builder()
 *             .name("Senior Research Analyst")
 *             .instructions("Find 3 key AI innovations.")
 *             .tools("serper.search")
 *             .build();
 *     }
 *
 *     @Task(order = 1)
 *     PipelineStep researchTask() {
 *         return PipelineStep.builder()
 *             .id("research_task")
 *             .agent("Senior Research Analyst")
 *             .input("query", "Latest AI frameworks")
 *             .build();
 *     }
 * }
 *
 * RunResult result = hubbers.runFlow(new ResearchFlow(), inputsNode);
 * }</pre>
 *
 * @see Agent
 * @see Task
 * @see FlowRunner
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pipeline {

    /**
     * The pipeline name. Used to register and invoke the pipeline in the artifact repository.
     */
    String value();
}
