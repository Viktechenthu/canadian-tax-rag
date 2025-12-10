package com.example.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.chroma.client.host:localhost}")
    private String chromaHost;

    @Value("${spring.ai.vectorstore.chroma.client.port:8000}")
    private int chromaPort;

    @Value("${spring.ai.vectorstore.chroma.collection-name:canadian_tax_documents}")
    private String collectionName;

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel,
                                   RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper) {

        String baseUrl = "http://" + chromaHost + ":" + chromaPort;

        ChromaApi chromaApi = new ChromaApi(
                baseUrl,
                restClientBuilder,
                objectMapper
        );

        // ⭐ 1.0.x requires chromaApi + embeddingModel passed into builder()
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
