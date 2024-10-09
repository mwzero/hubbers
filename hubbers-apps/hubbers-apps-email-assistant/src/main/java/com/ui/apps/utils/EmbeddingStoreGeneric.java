package com.ui.apps.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.dictionary.Index;

@Slf4j
public class EmbeddingStoreGeneric {
	
	protected final String indexName = "mail-assistant";
	
	EmbeddingStore<TextSegment> embeddingStore;
	EmbeddingModel embeddingModel;
	
	public EmbeddingStoreGeneric(
			EmbeddingModel embeddingModel,
			EmbeddingStore<TextSegment> embeddingStore) throws Exception {
		
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore; 

		/*
		embeddingStore = PineconeEmbeddingStore.builder()
		        .apiKey(System.getenv("PINECONE_API_KEY"))
		        .index(index)
		        .nameSpace(namespace)
		        .createIndex(PineconeServerlessIndexConfig.builder()
		                .cloud("AWS")
		                .region("us-east-1")
		                .dimension(embeddingModel.dimension())
		                .build())
		        .build();
		
		*/
		
		/*
    	Pinecone pc = new Pinecone.Builder(apiKey).build();
    	pc.createServerlessIndex(index, "cosine", 2, "aws", "us-east-1", DeletionProtection.DISABLED);
    	pcIndex = pc.getIndexConnection(index);
    	*/
	}
	
	public void add(File file, Map<String, ?> metadata) throws IOException {
		
		String content = new String(Files.readAllBytes(file.toPath()));
		TextSegment segment = TextSegment.from(content, Metadata.from(metadata));
		Embedding embedding = embeddingModel.embed(segment).content();
		embeddingStore.add(embedding, segment);
		//UpsertResponse response = pcIndex.upsert(content, embedding.vectorAsList(),"ns1");
		
	}

	public void add(String content, Map<String, ?> metadata) {
		
		
		TextSegment segment = TextSegment.from(content, Metadata.from(metadata));
		Embedding embedding = embeddingModel.embed(segment).content();
		embeddingStore.add(embedding, segment);
		//UpsertResponse response = pcIndex.upsert(content, embedding.vectorAsList(),"ns1");
		
	}
	
	public EmbeddingMatch<TextSegment> query ( File query) throws IOException {
		String content = new String(Files.readAllBytes(query.toPath()));
		return query(content);
		
	}
	public EmbeddingMatch<TextSegment> query (String query ) {
		
		Embedding queryEmbedding = embeddingModel.embed(query).content();
		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
		        .queryEmbedding(queryEmbedding)
		        .maxResults(1)
		        .build();
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
		
		EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);
		
		return embeddingMatch;
		
	}

	
}
