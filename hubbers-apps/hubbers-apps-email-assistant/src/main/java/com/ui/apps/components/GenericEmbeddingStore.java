package com.ui.apps.components;

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
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.dictionary.Index;

@Slf4j
public class GenericEmbeddingStore {
	
	protected final String indexName = "mail-assistant";
	
	EmbeddingStore<TextSegment> embeddingStore;
	EmbeddingModel embeddingModel;
	
	Index pcIndex;
	
	@Builder
	public GenericEmbeddingStore(EmbeddingModel embeddingModel, String index, String namespace) throws Exception {
		
		this.embeddingModel = embeddingModel;

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
		embeddingStore = PgVectorEmbeddingStore.builder()
                .host(System.getenv("DB_PG_HOST"))
                .port(Integer.parseInt(System.getenv("DB_PG_PORT")))
                .user(System.getenv("DB_PG_USER"))
                .password(System.getenv("DB_PG_PWD"))
                .database("postgres")
                .table(index)
                .dimension(384)
                //.dropTableFirst(true)
                .build();
    	
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
	
	public void query (String query ) {
		
		Embedding queryEmbedding = embeddingModel.embed(query).content();
		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
		        .queryEmbedding(queryEmbedding)
		        .maxResults(1)
		        .build();
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
		
		EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);
		System.out.println(embeddingMatch.score()); // 0.8144288515898701
		System.out.println(embeddingMatch.embedded().text()); // I like football.
	}

	
}
