package org.hubbers.annotation;

import com.fasterxml.jackson.databind.JsonNode;
import org.hubbers.app.RuntimeFacade;
import org.hubbers.execution.RunResult;
import org.hubbers.manifest.agent.AgentManifest;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.pipeline.PipelineManifest;
import org.hubbers.manifest.pipeline.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Executes a {@link Pipeline}-annotated flow class using the Hubbers runtime.
 *
 * <p>FlowRunner uses reflection to discover {@link Agent}-annotated methods (which
 * return {@link AgentManifest}) and {@link Task}-annotated methods (which return
 * {@link PipelineStep}). It registers all agents in the artifact repository, assembles
 * the pipeline steps in {@link Task#order()} sequence, registers the pipeline, then
 * delegates execution to {@link RuntimeFacade#runPipeline(String, JsonNode)}.</p>
 *
 * <p>Typical usage via {@link RuntimeFacade#runFlow(Object, JsonNode)}:
 * <pre>{@code
 * @Pipeline("research-pipeline")
 * class ResearchFlow {
 *
 *     @Agent
 *     AgentManifest researcher() {
 *         return AgentManifest.builder()
 *             .name("Senior Research Analyst")
 *             .instructions("Find 3 key AI innovations.")
 *             .tools("serper.search", "file.read")
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
 * </p>
 *
 * @see Pipeline
 * @see Agent
 * @see Task
 */
public class FlowRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowRunner.class);

    private final RuntimeFacade runtime;

    /**
     * Creates a FlowRunner backed by the given {@link RuntimeFacade}.
     *
     * @param runtime the runtime facade to use for agent registration and pipeline execution
     */
    public FlowRunner(RuntimeFacade runtime) {
        this.runtime = runtime;
    }

    /**
     * Convenience static method — equivalent to {@code new FlowRunner(runtime).run(flow, input)}.
     *
     * @param flow    an instance of a {@link Pipeline}-annotated class
     * @param runtime the Hubbers runtime facade
     * @param input   pipeline input as JSON
     * @return the execution result
     */
    public static RunResult run(Object flow, RuntimeFacade runtime, JsonNode input) {
        return new FlowRunner(runtime).run(flow, input);
    }

    /**
     * Executes the given flow object.
     *
     * <ol>
     *   <li>Validates that the class is annotated with {@link Pipeline}.</li>
     *   <li>Discovers and invokes all {@link Agent}-annotated methods; registers each
     *       returned {@link AgentManifest} in the artifact repository.</li>
     *   <li>Discovers and invokes all {@link Task}-annotated methods; sorts them by
     *       {@link Task#order()} and assembles the step list.</li>
     *   <li>Builds and registers the {@link PipelineManifest}.</li>
     *   <li>Delegates to {@link RuntimeFacade#runPipeline(String, JsonNode)}.</li>
     * </ol>
     *
     * @param flow  an instance of a {@link Pipeline}-annotated class
     * @param input pipeline input as JSON
     * @return the execution result
     * @throws IllegalArgumentException if the class is not annotated with {@link Pipeline}
     */
    public RunResult run(Object flow, JsonNode input) {
        Class<?> flowClass = flow.getClass();

        Pipeline pipelineAnnotation = flowClass.getAnnotation(Pipeline.class);
        if (pipelineAnnotation == null) {
            throw new IllegalArgumentException(
                "Flow class '" + flowClass.getSimpleName() + "' must be annotated with @Pipeline");
        }

        String pipelineName = pipelineAnnotation.value();
        log.info("Running flow '{}' from class '{}'", pipelineName, flowClass.getSimpleName());

        // Discover and register agents
        List<Method> agentMethods = findAnnotatedMethods(flowClass, Agent.class);
        for (Method method : agentMethods) {
            AgentManifest manifest = invokeReturning(flow, method, AgentManifest.class);
            ensureAgentMetadata(manifest, method);
            runtime.getArtifactRepository().addAgent(manifest);
            log.debug("Registered agent '{}' from method '{}'",
                manifest.getAgent().getName(), method.getName());
        }

        // Discover, sort, and collect steps
        List<Method> taskMethods = findAnnotatedMethods(flowClass, Task.class);
        taskMethods.sort(Comparator.comparingInt(m -> m.getAnnotation(Task.class).order()));

        List<PipelineStep> steps = new ArrayList<>();
        for (Method method : taskMethods) {
            PipelineStep step = invokeReturning(flow, method, PipelineStep.class);
            steps.add(step);
            log.debug("Added step '{}' (order={}) from method '{}'",
                step.getId(), method.getAnnotation(Task.class).order(), method.getName());
        }

        // Build and register the pipeline manifest
        PipelineManifest pipeline = new PipelineManifest();
        Metadata meta = new Metadata();
        meta.setName(pipelineName);
        pipeline.setPipeline(meta);
        pipeline.setSteps(steps);
        runtime.getArtifactRepository().addPipeline(pipeline);

        log.info("Assembled pipeline '{}' with {} step(s); starting execution", pipelineName, steps.size());
        return runtime.runPipeline(pipelineName, input);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Method> findAnnotatedMethods(Class<?> clazz,
            Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Method> result = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(annotation)) {
                result.add(m);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeReturning(Object target, Method method, Class<T> expectedType) {
        method.setAccessible(true);
        try {
            Object result = method.invoke(target);
            if (!expectedType.isInstance(result)) {
                throw new IllegalStateException(
                    "Method '" + method.getName() + "' must return " + expectedType.getSimpleName()
                    + " but returned " + (result == null ? "null" : result.getClass().getSimpleName()));
            }
            return (T) result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(
                "Error invoking '" + method.getName() + "': " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                "Cannot access method '" + method.getName() + "'", e);
        }
    }

    /**
     * Ensures the AgentManifest has a Metadata object. If the builder was used
     * correctly this is always present; guard here for defensive safety.
     */
    private void ensureAgentMetadata(AgentManifest manifest, Method method) {
        if (manifest.getAgent() == null) {
            Metadata meta = new Metadata();
            meta.setName(method.getName());
            manifest.setAgent(meta);
        }
    }
}
