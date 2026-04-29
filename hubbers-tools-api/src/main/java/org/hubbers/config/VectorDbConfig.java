package org.hubbers.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for the native vector database layer.
 *
 * <p>The first implementation is backed by local Lucene indexes, while keeping
 * the provider field explicit so future vector stores can be added without
 * changing the settings contract.</p>
 *
 * @since 0.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorDbConfig {
    /** Whether native vector database management is enabled in the runtime UI. */
    @Builder.Default
    private Boolean enabled = true;

    /** Vector database provider identifier, currently {@code lucene}. */
    @Builder.Default
    private String provider = "lucene";

    /** Root path where managed vector indexes are stored. */
    @Builder.Default
    private String rootPath = "./datasets/lucene/vector";

    /** Default index name used by builders and quick-start flows. */
    @Builder.Default
    private String defaultIndex = "default";

    /** Embedding strategy used for managed indexes. */
    @Builder.Default
    private String embeddingStrategy = "hashing";

    /** Number of vector dimensions used by the embedding strategy. */
    @Builder.Default
    private Integer dimensions = 256;

    /** Default number of nearest neighbors to return from searches. */
    @Builder.Default
    private Integer defaultTopK = 3;

    /** If true, production flows should expose only certified vector indexes. */
    @Builder.Default
    private Boolean certifiedOnly = false;

    /** Retention period for managed vector documents and ingestion metadata. */
    @Builder.Default
    private Integer retentionDays = 365;

    /** Approved local paths where managed vector indexes can be created. */
    private List<String> allowedPaths;
}