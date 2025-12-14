package com.example.rag.controller;

import com.example.rag.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Ingest PDFs from the configured resource location
     * POST /api/ingest
     */
    @PostMapping("/ingest")
    public Mono<ResponseEntity<Map<String, String>>> ingest() {
        log.info("Ingestion request received");
        return Mono.fromCallable(() -> ragService.ingestDocuments())
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", result
                )));
    }

    /**
     * Chat endpoint for RAG-based question answering
     * POST /api/chat
     * Body: { "question": "Your question here" }
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<Map<String, String>>> chat(@RequestBody Map<String, String> request) {
        String question = request.get("question");

        // Perform validation immediately (this is safe in the Netty thread)
        if (question == null || question.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", "Question is required"
            )));
        }

        log.info("Chat request received: {}", question);

        // Wrap the blocking service call and offload it
        return Mono.fromCallable(() -> ragService.chat(question))
                .subscribeOn(Schedulers.boundedElastic())

                // Map the response back to a reactive entity
                .map(response -> ResponseEntity.ok(Map.of(
                        "question", question,
                        "answer", response
                )))

                // Handle any errors that occur during the chat process
                .onErrorResume(e -> {
                    log.error("Error during chat processing for question: {}", question, e);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                            "error", "Internal server error during chat: " + e.getMessage()
                    )));
                });
    }

    /**
     * Search for similar documents
     * GET /api/search?query=your+query&topK=5
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        log.info("Search request received: {}", query);
        List<String> results = ragService.search(query, topK);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "results", results,
                "count", results.size()
        ));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "Spring AI RAG"
        ));
    }
}