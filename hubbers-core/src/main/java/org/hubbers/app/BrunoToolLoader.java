package org.hubbers.app;

import org.hubbers.manifest.agent.InputDefinition;
import org.hubbers.manifest.agent.OutputDefinition;
import org.hubbers.manifest.common.Metadata;
import org.hubbers.manifest.common.PropertyDefinition;
import org.hubbers.manifest.common.SchemaDefinition;
import org.hubbers.manifest.tool.ToolManifest;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
final class BrunoToolLoader {
    private static final Pattern BLOCK_START = Pattern.compile("(?m)^([A-Za-z][A-Za-z0-9:-]*)\\s*\\{");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}") ;
    private static final Set<String> HTTP_METHOD_TAGS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace", "connect"
    );

    private BrunoToolLoader() {
    }

    static List<ToolManifest> loadTools(Path repoRoot) {
        Path brunoRoot = repoRoot.resolve("bruno");
        if (!Files.isDirectory(brunoRoot)) {
            return List.of();
        }

        try (Stream<Path> collections = Files.list(brunoRoot)) {
            return collections
                    .filter(Files::isDirectory)
                    .sorted(Comparator.naturalOrder())
                    .map(BrunoToolLoader::loadCollectionTools)
                    .flatMap(Collection::stream)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot scan Bruno collections in " + brunoRoot, exception);
        }
    }

    private static List<ToolManifest> loadCollectionTools(Path collectionRoot) {
        BrunoCollectionContext collectionContext = loadCollectionContext(collectionRoot);

        try (Stream<Path> requestFiles = Files.walk(collectionRoot)) {
            return requestFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".bru"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("collection.bru"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("folder.bru"))
                    .filter(path -> !path.toString().contains("environments"))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> buildToolManifest(collectionRoot, path, collectionContext))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Bruno requests from " + collectionRoot, exception);
        }
    }

    private static BrunoCollectionContext loadCollectionContext(Path collectionRoot) {
        Path collectionFile = collectionRoot.resolve("collection.bru");
        if (!Files.exists(collectionFile)) {
            return new BrunoCollectionContext(Map.of(), Map.of());
        }

        List<BrunoBlock> blocks = parseBlocks(collectionFile);
        return new BrunoCollectionContext(
                parseDictionaryBlock(findBlock(blocks, "vars").orElse(null)),
                parseDictionaryBlock(findBlock(blocks, "headers").orElse(null))
        );
    }

    private static Optional<ToolManifest> buildToolManifest(
            Path collectionRoot,
            Path requestFile,
            BrunoCollectionContext collectionContext
    ) {
        List<BrunoBlock> blocks = parseBlocks(requestFile);
        Optional<BrunoBlock> methodBlock = blocks.stream()
                .filter(block -> HTTP_METHOD_TAGS.contains(block.name()))
                .findFirst();

        if (methodBlock.isEmpty()) {
            log.debug("Skipping Bruno file without HTTP method block: {}", requestFile);
            return Optional.empty();
        }

        Map<String, String> methodConfig = parseDictionaryBlock(methodBlock.get());
        String url = methodConfig.get("url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Bruno request is missing a URL: " + requestFile);
        }

        Map<String, String> meta = parseDictionaryBlock(findBlock(blocks, "meta").orElse(null));
        Map<String, String> requestHeaders = parseDictionaryBlock(findBlock(blocks, "headers").orElse(null));
        Map<String, String> mergedHeaders = new LinkedHashMap<>(collectionContext.headers());
        mergedHeaders.putAll(requestHeaders);

        Map<String, String> pathParams = parseDictionaryBlock(findBlock(blocks, "params:path").orElse(null));
        Map<String, String> queryParams = parseDictionaryBlock(findBlock(blocks, "params:query").orElse(null));
        Optional<BrunoBlock> bodyBlock = blocks.stream()
                .filter(block -> block.name().startsWith("body"))
                .findFirst();

        String toolName = buildToolName(collectionRoot, requestFile);
        ToolManifest manifest = new ToolManifest();
        manifest.setTool(buildMetadata(toolName, meta, requestFile));
        manifest.setType("http");
        manifest.setConfig(buildConfig(
                methodBlock.get().name(),
                url,
                collectionContext.variables(),
                pathParams,
                queryParams,
                mergedHeaders,
                bodyBlock.orElse(null)
        ));
        manifest.setInput(buildInputDefinition(url, collectionContext.variables(), pathParams, queryParams, mergedHeaders, bodyBlock.orElse(null)));
        manifest.setOutput(buildOutputDefinition());
        return Optional.of(manifest);
    }

    private static Metadata buildMetadata(String toolName, Map<String, String> meta, Path requestFile) {
        Metadata metadata = new Metadata();
        metadata.setName(toolName);
        metadata.setVersion("1.0.0");
        metadata.setDescription(meta.getOrDefault("name", "Generated from Bruno request " + requestFile.getFileName()));
        return metadata;
    }

    private static Map<String, Object> buildConfig(
            String methodTag,
            String url,
            Map<String, String> collectionVariables,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<String, String> headers,
            BrunoBlock bodyBlock
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("base_url", url);
        config.put("method", methodTag.toUpperCase());

        if (!collectionVariables.isEmpty()) {
            config.put("variables", collectionVariables);
        }
        if (!pathParams.isEmpty()) {
            config.put("path_params", pathParams);
        }
        if (!queryParams.isEmpty()) {
            config.put("query_params", queryParams);
        }
        if (!headers.isEmpty()) {
            config.put("headers", headers);
        }
        if (bodyBlock != null) {
            config.put("body_type", bodyType(bodyBlock.name()));
            config.put("body_template", bodyBlock.content().trim());
        }

        return config;
    }

    private static InputDefinition buildInputDefinition(
            String url,
            Map<String, String> collectionVariables,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            Map<String, String> headers,
            BrunoBlock bodyBlock
    ) {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setType("object");

        pathParams.forEach((name, value) -> schema.getProperties().put(name,
                property("string", false, "Bruno path parameter")));
        queryParams.forEach((name, value) -> schema.getProperties().put(name,
                property("string", false, "Bruno query parameter")));

        Set<String> placeholders = new LinkedHashSet<>();
        placeholders.addAll(extractTemplateNames(url));
        placeholders.addAll(extractTemplateNames(pathParams.values()));
        placeholders.addAll(extractTemplateNames(queryParams.values()));
        placeholders.addAll(extractTemplateNames(headers.values()));
        if (bodyBlock != null) {
            placeholders.addAll(extractTemplateNames(bodyBlock.content()));
        }

        placeholders.stream()
                .filter(name -> !collectionVariables.containsKey(name))
                .forEach(name -> schema.getProperties().putIfAbsent(name,
                        property("string", true, "Bruno template variable")));

        InputDefinition inputDefinition = new InputDefinition();
        inputDefinition.setSchema(schema);
        return inputDefinition;
    }

    private static OutputDefinition buildOutputDefinition() {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setType("object");

        OutputDefinition outputDefinition = new OutputDefinition();
        outputDefinition.setSchema(schema);
        return outputDefinition;
    }

    private static PropertyDefinition property(String type, boolean required, String description) {
        PropertyDefinition property = new PropertyDefinition();
        property.setType(type);
        property.setRequired(required);
        property.setDescription(description);
        return property;
    }

    private static String buildToolName(Path collectionRoot, Path requestFile) {
        List<String> nameParts = new ArrayList<>();
        nameParts.add("bruno");
        nameParts.add(normalizeNamePart(collectionRoot.getFileName().toString()));

        Path relativePath = collectionRoot.relativize(requestFile);
        for (Path pathPart : relativePath) {
            String rawPart = pathPart.getFileName().toString();
            String withoutExtension = rawPart.endsWith(".bru")
                    ? rawPart.substring(0, rawPart.length() - 4)
                    : rawPart;
            nameParts.add(normalizeNamePart(withoutExtension));
        }

        return String.join(".", nameParts);
    }

    private static String normalizeNamePart(String rawValue) {
        return rawValue
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String bodyType(String blockName) {
        if (!blockName.contains(":")) {
            return "json";
        }

        return blockName.substring(blockName.indexOf(':') + 1);
    }

    private static Optional<BrunoBlock> findBlock(List<BrunoBlock> blocks, String blockName) {
        return blocks.stream()
                .filter(block -> block.name().equals(blockName))
                .findFirst();
    }

    private static Map<String, String> parseDictionaryBlock(BrunoBlock block) {
        if (block == null) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String line : block.content().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("~")) {
                continue;
            }

            int separator = trimmed.indexOf(':');
            if (separator < 0) {
                continue;
            }

            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (!key.isBlank() && !key.startsWith("~")) {
                values.put(key, stripQuotes(value));
            }
        }
        return values;
    }

    private static List<BrunoBlock> parseBlocks(Path filePath) {
        try {
            String content = Files.readString(filePath);
            List<BrunoBlock> blocks = new ArrayList<>();
            int searchIndex = 0;

            while (searchIndex < content.length()) {
                Matcher matcher = BLOCK_START.matcher(content);
                matcher.region(searchIndex, content.length());
                if (!matcher.find()) {
                    break;
                }

                String blockName = matcher.group(1);
                int braceStart = content.indexOf('{', matcher.start(1) + blockName.length());
                int braceEnd = findMatchingBrace(content, braceStart);
                String blockContent = content.substring(braceStart + 1, braceEnd).trim();
                blocks.add(new BrunoBlock(blockName, blockContent));
                searchIndex = braceEnd + 1;
            }

            return blocks;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Bruno file " + filePath, exception);
        }
    }

    private static int findMatchingBrace(String content, int braceStart) {
        int depth = 0;
        for (int index = braceStart; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        throw new IllegalStateException("Unbalanced Bruno block braces");
    }

    private static Set<String> extractTemplateNames(String value) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = TEMPLATE_PATTERN.matcher(value);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> extractTemplateNames(Collection<String> values) {
        Set<String> names = new LinkedHashSet<>();
        values.forEach(value -> names.addAll(extractTemplateNames(value)));
        return names;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record BrunoBlock(String name, String content) {
    }

    private record BrunoCollectionContext(Map<String, String> variables, Map<String, String> headers) {
    }
}