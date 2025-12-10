package com.example.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for ingesting tax documents into the vector store.
 *
 * LEARNING NOTES:
 * - Document: Spring AI's representation of a text chunk with metadata
 * - DocumentReader: Reads files (PDF, text) and converts to Documents
 * - TextSplitter: Breaks large documents into smaller chunks
 *   (LLMs have token limits, and smaller chunks = more precise retrieval)
 * - VectorStore.add(): Embeds documents and stores them
 */
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("${documents.path}")
    private String documentsPath;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Ingests all PDF and text files from the configured directory.
     *
     * Process:
     * 1. Read files from directory
     * 2. Parse PDFs or text files
     * 3. Split into chunks (512 tokens with 50 token overlap)
     * 4. Generate embeddings and store in vector database
     *
     * @return Number of documents ingested
     */
    public int ingestDocuments() throws IOException {
        File documentsDir = new File(documentsPath);

        if (!documentsDir.exists()) {
            documentsDir.mkdirs();
            System.out.println("Created documents directory: " + documentsPath);
            System.out.println("Please add your Canadian tax documents (PDF or TXT) to this directory");
            return 0;
        }

        List<Document> allDocuments = new ArrayList<>();

        // Find all PDF and TXT files
        try (Stream<Path> paths = Files.walk(documentsDir.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.toString().toLowerCase();
                        return fileName.endsWith(".pdf") || fileName.endsWith(".txt");
                    })
                    .forEach(path -> {
                        try {
                            List<Document> docs = loadDocument(path.toFile());
                            allDocuments.addAll(docs);
                            System.out.println("Loaded " + docs.size() + " chunks from: " + path.getFileName());
                        } catch (Exception e) {
                            System.err.println("Error loading " + path.getFileName() + ": " + e.getMessage());
                        }
                    });
        }

        if (!allDocuments.isEmpty()) {
            // Store documents in vector store (this creates embeddings automatically)
            vectorStore.add(allDocuments);
            System.out.println("Successfully ingested " + allDocuments.size() + " document chunks into Chroma DB");
        }

        return allDocuments.size();
    }

    /**
     * Loads a single document (PDF or TXT) and splits it into chunks.
     *
     * Why split documents?
     * - LLMs have context limits
     * - Smaller chunks = more precise retrieval
     * - Overlap ensures context isn't lost at boundaries
     *
     * @param file The file to load
     * @return List of document chunks
     */
    private List<Document> loadDocument(File file) throws IOException {
        List<Document> documents;

        if (file.getName().toLowerCase().endsWith(".pdf")) {
            // PDF Configuration
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageBottomMargin(0)
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfTopTextLinesToDelete(0)
                            .build())
                    .build();

            Resource resource = new FileSystemResource(file);
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
            documents = pdfReader.get();

        } else {
            // Plain text file
            String content = Files.readString(file.toPath());
            Document doc = new Document(content);
            doc.getMetadata().put("source", file.getName());
            documents = List.of(doc);
        }

        // Split into chunks
        // 512 tokens per chunk, 50 token overlap to maintain context
        TokenTextSplitter splitter = new TokenTextSplitter(512, 50, 5, 1000, true);
        return splitter.apply(documents);
    }
}