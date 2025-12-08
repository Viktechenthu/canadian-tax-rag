package com.example.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configuration for the Vector Store.
 *
 * LEARNING NOTES:
 * - VectorStore: A database that stores document embeddings (vectors)
 * - SimpleVectorStore: File-based implementation that saves to JSON
 * - EmbeddingModel: Converts text to numerical vectors using Ollama
 * - Vectors allow semantic search (finding similar meaning, not just keywords)
 */
@Configuration
public class VectorStoreConfig {

    @Value("${vector.store.file-path}")
    private String vectorStoreFilePath;

    /**
     * Creates a VectorStore bean that persists to a local file.
     *
     * How it works:
     * 1. Takes an EmbeddingModel (auto-configured from application.properties)
     * 2. Creates SimpleVectorStore that saves embeddings to JSON file
     * 3. If file exists, loads previous embeddings (persistence!)
     *
     * @param embeddingModel The Ollama embedding model
     * @return Configured VectorStore
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = new SimpleVectorStore(embeddingModel);

        File vectorStoreFile = new File(vectorStoreFilePath);

        // Load existing vector store if it exists
        if (vectorStoreFile.exists()) {
            System.out.println("Loading existing vector store from: " + vectorStoreFilePath);
            vectorStore.load(vectorStoreFile);
        } else {
            System.out.println("Creating new vector store at: " + vectorStoreFilePath);
            // Ensure parent directory exists
            vectorStoreFile.getParentFile().mkdirs();
        }

        return vectorStore;
    }
}