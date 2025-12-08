package com.example.rag.controller;

import com.example.rag.service.DocumentIngestionService;
import com.example.rag.service.TaxRagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for the Tax RAG API.
 *
 * LEARNING NOTES:
 * - @RestController: Combines @Controller + @ResponseBody
 * - @PostMapping/@GetMapping: Maps HTTP methods to functions
 * - ResponseEntity: Provides full control over HTTP response
 *
 * Endpoints:
 * - POST /api/ingest: Load documents into vector store
 * - POST /api/ask: Ask a question about taxes
 * - GET /api/retrieve: See what documents match a query
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend calls
public class TaxRagController {

    private final DocumentIngestionService ingestionService;
    private final TaxRagService ragService;

    public TaxRagController(DocumentIngestionService ingestionService,
                            TaxRagService ragService) {
        this.ingestionService = ingestionService;
        this.ragService = ragService;
    }

    /**
     * Ingests all documents from the configured directory.
     *
     * Usage:
     * POST http://localhost:8080/api/ingest
     *
     * Response:
     * {
     *   "message": "Successfully ingested 42 document chunks",
     *   "documentsIngested": 42
     * }
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocuments() {
        try {
            int count = ingestionService.ingestDocuments();
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully ingested " + count + " document chunks",
                    "documentsIngested", count
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to ingest documents: " + e.getMessage()));
        }
    }

    /**
     * Asks a question about Canadian taxes.
     *
     * Usage:
     * POST http://localhost:8080/api/ask
     * Content-Type: application/json
     *
     * Body:
     * {
     *   "question": "What is the TFSA contribution limit for 2024?"
     * }
     *
     * Response:
     * {
     *   "question": "What is the TFSA contribution limit for 2024?",
     *   "answer": "According to the tax documents, the TFSA contribution limit..."
     * }
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.get("question");

        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question cannot be empty"));
        }

        String answer = ragService.askQuestion(question);

        return ResponseEntity.ok(Map.of(
                "question", question,
                "answer", answer
        ));
    }

    /**
     * Retrieves documents that match a query (for debugging).
     * Useful to see what context the RAG system is using.
     *
     * Usage:
     * GET http://localhost:8080/api/retrieve?question=TFSA+limits
     *
     * Response:
     * {
     *   "question": "TFSA limits",
     *   "documents": [
     *     {
     *       "content": "The TFSA contribution limit...",
     *       "source": "tax-guide-2024.pdf",
     *       "similarity": 0.89
     *     }
     *   ]
     * }
     */
    @GetMapping("/retrieve")
    public ResponseEntity<Map<String, Object>> retrieveDocuments(@RequestParam String question) {
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question cannot be empty"));
        }

        List<TaxRagService.DocumentInfo> docs = ragService.getRetrievedDocuments(question);

        return ResponseEntity.ok(Map.of(
                "question", question,
                "documents", docs
        ));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Canadian Tax RAG"
        ));
    }
}