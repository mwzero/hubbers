package org.hubbers.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.util.JacksonFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Exports {@link ExecutionTrace} data as JSON files compatible with OpenTelemetry trace format.
 *
 * <p>This is a lightweight, zero-dependency exporter that writes trace spans to a configurable
 * directory. The output can be ingested by OTLP-compatible backends like Jaeger or Zipkin
 * via file-based collection.</p>
 *
 * @since 0.1.0
 */
@Slf4j
public class TraceExporter {

    private final Path exportDir;
    private final ObjectMapper mapper;

    /**
     * Create a trace exporter that writes to the given directory.
     *
     * @param exportDir directory to write trace files to
     */
    public TraceExporter(Path exportDir) {
        this(exportDir, JacksonFactory.jsonMapper());
    }

    /**
     * Create a trace exporter with a custom ObjectMapper.
     *
     * @param exportDir directory to write trace files to
     * @param mapper Jackson ObjectMapper
     */
    public TraceExporter(Path exportDir, ObjectMapper mapper) {
        this.exportDir = exportDir;
        this.mapper = mapper;
    }

    /**
     * Export execution data as a trace JSON file.
     *
     * @param executionId unique execution identifier
     * @param artifactType the artifact type (agent, tool, pipeline, skill)
     * @param artifactName the artifact name
     * @param status execution status
     * @param metadata execution metadata with timing and token info
     * @param trace execution trace with detailed step/iteration data (may be null)
     * @return the path of the written trace file
     * @throws IOException if writing fails
     */
    public Path export(String executionId, String artifactType, String artifactName,
                       ExecutionStatus status, ExecutionMetadata metadata,
                       ExecutionTrace trace) throws IOException {
        Files.createDirectories(exportDir);

        String traceId = UUID.randomUUID().toString().replace("-", "");
        ObjectNode traceDoc = mapper.createObjectNode();
        traceDoc.put("traceId", traceId);
        traceDoc.put("serviceName", "hubbers");
        traceDoc.put("exportedAt", Instant.now().toString());

        // Root span
        ObjectNode rootSpan = traceDoc.putObject("rootSpan");
        rootSpan.put("spanId", generateSpanId());
        rootSpan.put("operationName", artifactType + "/" + artifactName);
        rootSpan.put("startTime", metadata.getStartedAt());
        rootSpan.put("endTime", metadata.getEndedAt());
        rootSpan.put("durationMs", metadata.getEndedAt() - metadata.getStartedAt());
        rootSpan.put("status", status.name());

        // Token usage attributes
        ObjectNode attributes = rootSpan.putObject("attributes");
        attributes.put("hubbers.artifact.type", artifactType);
        attributes.put("hubbers.artifact.name", artifactName);
        attributes.put("hubbers.tokens.prompt", metadata.getPromptTokens());
        attributes.put("hubbers.tokens.completion", metadata.getCompletionTokens());
        attributes.put("hubbers.tokens.total", metadata.getTotalTokens());

        // Child spans from trace
        ArrayNode childSpans = traceDoc.putArray("spans");
        if (trace != null) {
            exportPipelineSteps(childSpans, trace, traceId);
            exportAgentIterations(childSpans, trace, traceId);
            exportSkillInvocations(childSpans, trace, traceId);
        }

        String fileName = String.format("trace-%s-%s.json",
                executionId, traceId.substring(0, 8));
        Path filePath = exportDir.resolve(fileName);
        mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), traceDoc);

        log.info("Exported trace to {}", filePath);
        return filePath;
    }

    private void exportPipelineSteps(ArrayNode spans, ExecutionTrace trace, String traceId) {
        for (PipelineStepTrace step : trace.getPipelineSteps()) {
            ObjectNode span = spans.addObject();
            span.put("spanId", generateSpanId());
            span.put("traceId", traceId);
            span.put("operationName", "pipeline.step/" + step.getStepName());
            span.put("startTime", step.getStartTime());
            span.put("endTime", step.getEndTime());
            span.put("durationMs", step.getDurationMs());
            span.put("status", step.getStatus() != null ? step.getStatus().name() : "UNKNOWN");
            span.put("artifactType", step.getArtifactType());
            span.put("artifactName", step.getArtifactName());
        }
    }

    private void exportAgentIterations(ArrayNode spans, ExecutionTrace trace, String traceId) {
        for (AgentIterationTrace iter : trace.getIterations()) {
            ObjectNode span = spans.addObject();
            span.put("spanId", generateSpanId());
            span.put("traceId", traceId);
            span.put("operationName", "agent.iteration/" + iter.getIterationNumber());
            span.put("durationMs", iter.getDurationMs());
            span.put("complete", iter.isComplete());
            if (iter.getToolCalls() != null) {
                ArrayNode tools = span.putArray("toolCalls");
                for (var tc : iter.getToolCalls()) {
                    tools.addObject()
                            .put("name", tc.getToolName())
                            .put("success", tc.isSuccess())
                            .put("durationMs", tc.getDurationMs());
                }
            }
        }
    }

    private void exportSkillInvocations(ArrayNode spans, ExecutionTrace trace, String traceId) {
        for (SkillInvocationTrace skill : trace.getSkillInvocations()) {
            ObjectNode span = spans.addObject();
            span.put("spanId", generateSpanId());
            span.put("traceId", traceId);
            span.put("operationName", "skill/" + skill.getSkillName());
        }
    }

    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
