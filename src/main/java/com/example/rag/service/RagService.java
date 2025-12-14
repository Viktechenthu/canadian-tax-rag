package com.example.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;

    @Value("${pdf.resource.location}")
    private String pdfResourceLocation;

    // Constructor for dependency injection
    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClientBuilder = chatClientBuilder;
    }

    private static final String PROMPT_TEMPLATE = """
            You are a helpful assistant. Answer the question based on the following context.
            If you cannot find the answer in the context, say "I don't have enough information to answer that question."
            
            Context:
            {context}
            
            Question: {question}
            
            Answer:
            """;

    /**
     * Ingests PDF documents from the configured resource location
     */
    public String ingestDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(pdfResourceLocation + "*.pdf");

            if (resources.length == 0) {
                return "No PDF files found in " + pdfResourceLocation;
            }

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("Processing PDF: {}", resource.getFilename());

                // Read PDF
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
                List<Document> documents = pdfReader.get();

                // Add metadata
                documents.forEach(doc -> {
                    doc.getMetadata().put("source", resource.getFilename());
                });

                allDocuments.addAll(documents);
            }

            // Split documents into chunks
            TextSplitter textSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
            List<Document> splitDocuments = textSplitter.apply(allDocuments);

            // Store in vector database
            vectorStore.add(splitDocuments);

            log.info("Successfully ingested {} documents from {} PDF files",
                    splitDocuments.size(), resources.length);

            return String.format("Successfully ingested %d document chunks from %d PDF files",
                    splitDocuments.size(), resources.length);

        } catch (IOException e) {
            log.error("Error ingesting documents", e);
            return "Error ingesting documents: " + e.getMessage();
        }
    }

    /**
     * Performs RAG-based chat by retrieving relevant context and generating response
     */
    public String chat(String question) {
        try {
            // Retrieve relevant documents
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(question).topK(5).build()
            );

            if (relevantDocs.isEmpty()) {
                return "No relevant information found in the knowledge base.";
            }

            // Prepare context from retrieved documents
            String context = relevantDocs.stream()
                    .map(Document::getFormattedContent)
                    .collect(Collectors.joining("\n\n"));

            // Create prompt with context
            PromptTemplate promptTemplate = new PromptTemplate(PROMPT_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "context", context,
                    "question", question
            ));

            // Generate response using Ollama
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt(prompt).call().content();

            log.info("Question: {}", question);
            log.info("Retrieved {} relevant documents", relevantDocs.size());

            return response;

        } catch (Exception e) {
            log.error("Error during chat", e);
            return "Error processing your question: " + e.getMessage();
        }
    }

    /**
     * Searches for similar documents without generating a response
     */
    public List<String> search(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        );

        return results.stream()
                .map(doc -> {
                    String source = (String) doc.getMetadata().get("source");
                    return String.format("[Source: %s]\n%s",
                            source != null ? source : "Unknown",
                            doc.getFormattedContent());
                })
                .collect(Collectors.toList());
    }
}