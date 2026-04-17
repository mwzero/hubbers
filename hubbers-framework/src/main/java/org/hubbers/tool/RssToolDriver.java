package org.hubbers.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hubbers.manifest.tool.ToolManifest;
import org.hubbers.util.HttpRequestBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/**
 * Tool driver for fetching and parsing RSS/Atom feeds.
 * 
 * <p>Supports both RSS 2.0 and Atom feed formats. Fetches multiple feeds
 * and aggregates items with configurable limits.</p>
 * 
 * <p>Configuration in tool.yaml:
 * <pre>{@code
 * type: rss
 * config:
 *   limit: 20  # Optional, default 20, max 200
 * }</pre>
 * 
 * <p>Input format:
 * <pre>{@code
 * {
 *   \"feeds\": [
 *     \"https://example.com/feed.xml\",
 *     \"https://another.com/rss\"
 *   ],
 *   \"limit\": 10  # Optional override
 * }
 * }</pre>
 * 
 * @since 0.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class RssToolDriver implements ToolDriver {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    @Override
    public String type() {
        return "rss";
    }

    @Override
    public JsonNode execute(ToolManifest manifest, JsonNode input) {
        ArrayNode feeds = input.path("feeds").isArray() ? (ArrayNode) input.path("feeds") : mapper.createArrayNode();
        int limit = resolveLimit(manifest, input);
        ArrayNode items = mapper.createArrayNode();

        for (JsonNode feedNode : feeds) {
            if (!feedNode.isTextual()) {
                continue;
            }
            String feedUrl = feedNode.asText();
            if (feedUrl == null || feedUrl.isBlank()) {
                continue;
            }
            collectFromFeed(feedUrl, items, limit);
            if (items.size() >= limit) {
                break;
            }
        }

        ObjectNode output = mapper.createObjectNode();
        output.set("items", items);
        return output;
    }

    private void collectFromFeed(String feedUrl, ArrayNode outputItems, int limit) {
        try {
            // Use HttpRequestBuilder for cleaner HTTP operations
            HttpRequestBuilder builder = new HttpRequestBuilder(httpClient, mapper);
            String xmlContent = builder.get(feedUrl)
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Hubbers/1.0")
                    .executeForString();

            Document document = parseXml(xmlContent, feedUrl);
            String source = firstText(document, "channel", "title");
            if (source == null || source.isBlank()) {
                source = feedUrl;
            }

            NodeList entries = document.getElementsByTagName("item");
            if (entries.getLength() == 0) {
                entries = document.getElementsByTagName("entry");
            }

            for (int i = 0; i < entries.getLength() && outputItems.size() < limit; i++) {
                Node node = entries.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element item = (Element) node;
                ObjectNode out = mapper.createObjectNode();
                out.put("source", source);
                out.put("feed_url", feedUrl);
                out.put("title", textOrEmpty(childText(item, "title")));
                out.put("link", resolveLink(item));
                out.put("published_at", textOrEmpty(firstNonBlank(
                        childText(item, "pubDate"),
                        childText(item, "published"),
                        childText(item, "updated")
                )));
                out.put("summary", textOrEmpty(firstNonBlank(
                        childText(item, "description"),
                        childText(item, "summary")
                )));
                out.put("content", textOrEmpty(firstNonBlank(
                        childText(item, "content:encoded"),
                        childText(item, "content"),
                        childText(item, "description"),
                        childText(item, "summary")
                )));
                outputItems.add(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("RSS fetch failed for " + feedUrl, e);
        }
    }

    private Document parseXml(String xml, String feedUrl) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
            }
            return factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            String hint = xml.toLowerCase().contains("<html") || xml.toLowerCase().contains("<!doctype html") 
                ? " (received HTML instead of RSS/XML - check if the URL is correct)"
                : "";
            throw new IllegalStateException("Invalid RSS/XML from " + feedUrl + hint + ": " + e.getMessage(), e);
        }
    }

    private String firstText(Document document, String parentTag, String childTag) {
        NodeList parents = document.getElementsByTagName(parentTag);
        if (parents.getLength() == 0) {
            return null;
        }
        Node parent = parents.item(0);
        if (parent.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        return childText((Element) parent, childTag);
    }

    private String resolveLink(Element item) {
        String link = childText(item, "link");
        if (link != null && !link.isBlank()) {
            return link.trim();
        }
        NodeList links = item.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element linkEl = (Element) node;
                String href = linkEl.getAttribute("href");
                if (href != null && !href.isBlank()) {
                    return href.trim();
                }
            }
        }
        return "";
    }

    private String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        if (node == null) {
            return null;
        }
        String value = node.getTextContent();
        return value == null ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private int resolveLimit(ToolManifest manifest, JsonNode input) {
        int limit = DEFAULT_LIMIT;
        JsonNode inputLimit = input.get("limit");
        if (inputLimit != null && inputLimit.isNumber()) {
            limit = inputLimit.asInt();
        } else if (manifest.getConfig() != null) {
            Object configLimit = manifest.getConfig().get("default_limit");
            if (configLimit instanceof Number number) {
                limit = number.intValue();
            } else if (configLimit != null) {
                try {
                    limit = Integer.parseInt(configLimit.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
