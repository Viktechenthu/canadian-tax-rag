package com.example.rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service implementing the RAG (Retrieval Augmented Generation) pattern.
 *
 * LEARNING NOTES:
 * RAG Flow:
 * 1. User asks a question
 * 2. Convert question to embedding
 * 3. Search vector store for similar document chunks
 * 4. Combine retrieved chunks with user question
 * 5. Send to LLM to generate informed answer
 *
 * Why RAG?
 * - LLMs have knowledge cutoffs
 * - RAG provides current, domain-specific information
 * - Reduces hallucinations by grounding answers in documents
 */
@Service
public class TaxRagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    // Prompt template that combines context with question
    private static final String PROMPT_TEMPLATE = """
            You are a helpful assistant specialized in Canadian tax law and regulations.
            Use the following context from Canadian tax documents to answer the question.
            If you cannot find the answer in the context, say so honestly.
            
            Context:
            {context}
            
            Question: {question}
            
            Answer:
            """;

    public TaxRagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        // ChatClient provides fluent API for interacting with LLM
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Answers a tax-related question using RAG.
     *
     * Process:
     * 1. Embed the question (convert to vector)
     * 2. Find top K similar documents from vector store
     * 3. Construct prompt with retrieved context
     * 4. Send to LLM for answer generation
     *
     * @param question User's question about Canadian taxes
     * @return AI-generated answer based on retrieved documents
     */
    public String askQuestion(String question) {
        // Step 1: Retrieve relevant documents
        // topK=5 means get 5 most similar document chunks
        // similarityThreshold=0.7 means only include chunks with >70% similarity
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        if (relevantDocs.isEmpty()) {
            return "I couldn't find any relevant information in the tax documents. " +
                    "Please ensure documents have been ingested or try rephrasing your question.";
        }

        // Step 2: Combine document contents into context
        String context = relevantDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        System.out.println("Retrieved " + relevantDocs.size() + " relevant document chunks");
        System.out.println("Context length: " + context.length() + " characters");

        // Step 3: Create prompt with template
        PromptTemplate promptTemplate = new PromptTemplate(PROMPT_TEMPLATE);
        Prompt prompt = promptTemplate.create(Map.of(
                "context", context,
                "question", question
        ));

        // Step 4: Get response from LLM
        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    /**
     * Gets detailed information about retrieved documents.
     * Useful for debugging and understanding what context was used.
     *
     * @param question The question to search for
     * @return List of documents with metadata
     */
    public List<DocumentInfo> getRetrievedDocuments(String question) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        List<Document> docs = vectorStore.similaritySearch(searchRequest);

        return docs.stream()
                .map(doc -> new DocumentInfo(
                        doc.getContent(),
                        doc.getMetadata().get("source") != null ?
                                doc.getMetadata().get("source").toString() : "Unknown",
                        doc.getMetadata().get("distance") != null ?
                                (Double) doc.getMetadata().get("distance") : 0.0
                ))
                .toList();
    }

    /**
     * Simple record to hold document information for API responses.
     */
    public record DocumentInfo(String content, String source, double similarity) {}
}