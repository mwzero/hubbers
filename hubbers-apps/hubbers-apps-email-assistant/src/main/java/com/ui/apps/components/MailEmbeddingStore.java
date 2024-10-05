package com.ui.apps.components;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailEmbeddingStore extends GenericEmbeddingStore {
	
	
	public MailEmbeddingStore(EmbeddingModel embeddingModel, String index,
			String namespace) throws Exception {
		
		super(embeddingModel, index, namespace);
	}

}
