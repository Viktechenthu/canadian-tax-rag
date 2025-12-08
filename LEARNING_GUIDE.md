# RAG System Learning Guide

This guide explains the core concepts behind your Canadian Tax RAG system in depth.

## Table of Contents
1. [What is RAG?](#what-is-rag)
2. [Understanding Vector Embeddings](#understanding-vector-embeddings)
3. [How Vector Similarity Search Works](#how-vector-similarity-search-works)
4. [Text Splitting Strategies](#text-splitting-strategies)
5. [Spring AI Architecture](#spring-ai-architecture)
6. [Ollama Local LLMs](#ollama-local-llms)
7. [Common Challenges & Solutions](#common-challenges--solutions)

---

## What is RAG?

**Retrieval Augmented Generation** combines two powerful techniques:

### Traditional LLM (without RAG)
```
User: "What's the 2024 TFSA limit?"
  ↓
LLM (only knows up to training date)
  ↓
Answer: "I don't have information past my training cutoff"
```

❌ **Problems:**
- Knowledge cutoff (outdated information)
- Can't access your specific documents
- May hallucinate (make up information)

### RAG-Enhanced LLM
```
User: "What's the 2024 TFSA limit?"
  ↓
1. Search your tax documents
  ↓
2. Find relevant chunks: "TFSA 2024 limit is $7,000..."
  ↓
3. Add chunks to LLM context
  ↓
LLM: "According to your documents, the 2024 TFSA limit is $7,000"
```

✅ **Benefits:**
- Current information from your documents
- Grounded in facts (reduces hallucinations)
- Domain-specific knowledge
- Can cite sources

### When to Use RAG vs Fine-Tuning

**Use RAG when:**
- You have frequently updated documents
- You want to cite sources
- Your data changes often
- You need quick setup

**Use Fine-Tuning when:**
- You want to change the model's behavior/style
- You need it to learn new formats
- You have lots of training data
- Data doesn't change often

---

## Understanding Vector Embeddings

### What Are Embeddings?

Embeddings convert text into numbers (vectors) that capture semantic meaning.

**Example:**
```
Text: "Canada Revenue Agency"
      ↓ (embedding model)
Vector: [0.23, -0.45, 0.67, ..., 0.12]  // 768 numbers
```

### Why Numbers?

Computers can't understand "similar meaning" in text, but they can measure distance between numbers!

**Semantic Similarity Example:**
```
"TFSA contribution limit" → [0.2, 0.8, 0.3, ...]
"TFSA annual maximum"     → [0.3, 0.7, 0.4, ...]  ← Similar!
"banana recipe"           → [0.9, 0.1, 0.2, ...]  ← Very different!
```

### How Embedding Models Work

1. **Input:** Text string
2. **Process:** Neural network processes each word in context
3. **Output:** Fixed-size vector (e.g., 768 dimensions)

**nomic-embed-text** (the model we use):
- Fast and efficient
- 768-dimensional vectors
- Trained on diverse text
- Good for retrieval tasks

### Vector Dimensions

Think of dimensions like characteristics:
- Dimension 1: Tax-related? (0.9)
- Dimension 2: Government context? (0.8)
- Dimension 3: Financial? (0.7)
- ... (765 more dimensions)

More dimensions = more nuanced understanding, but also more computation.

---

## How Vector Similarity Search Works

### Cosine Similarity

The most common way to compare vectors:

```
Similarity = cos(θ) = (A · B) / (||A|| × ||B||)

Where:
- A, B are vectors
- · is dot product
- || || is vector magnitude
- θ is angle between vectors
```

**Intuition:** Smaller angle = more similar meaning

```
Similar vectors:    Different vectors:
    A →            A →
     ↘ θ=10°              ↘ θ=80°
      B →                  ↙
                          B
```

### Search Process

1. **User Query:** "TFSA limits"
2. **Embed Query:** `[0.2, 0.8, 0.3, ...]`
3. **Compare with all stored vectors:**
   ```
   Doc 1: [0.3, 0.7, 0.4, ...] → Similarity: 0.89 ✓
   Doc 2: [0.9, 0.1, 0.2, ...] → Similarity: 0.32 ✗
   Doc 3: [0.2, 0.8, 0.3, ...] → Similarity: 0.95 ✓
   ```
4. **Return Top K:** Docs 3 and 1 (highest similarity)

### Similarity Threshold

In the code: `withSimilarityThreshold(0.7)`

This means: "Only return documents with >70% similarity"

**Choosing the right threshold:**
- **0.9+**: Very strict, only nearly identical matches
- **0.7-0.9**: Good balance (recommended)
- **0.5-0.7**: More lenient, may include tangential results
- **<0.5**: Too loose, lots of irrelevant results

---

## Text Splitting Strategies

### Why Split Documents?

**Problem:** LLMs have token limits (usually 4K-32K tokens)
- Your documents might be 100+ pages
- Full context won't fit in the LLM

**Solution:** Split into chunks and retrieve only relevant parts

### Our Strategy: Token-Based Splitting

```java
TokenTextSplitter splitter = new TokenTextSplitter(
    512,   // chunkSize: target tokens per chunk
    50,    // chunkOverlap: shared tokens between chunks
    5,     // minChunkSize: minimum tokens
    1000,  // maxChunkSize: maximum tokens
    true   // keepSeparator: preserve structure
);
```

### Why Overlap?

**Without Overlap:**
```
Chunk 1: "...deduct business expenses. |"
Chunk 2: "| The CRA defines eligible..."
         ↑ Context lost!
```

**With Overlap (50 tokens):**
```
Chunk 1: "...deduct business expenses. The CRA..."
Chunk 2: "...business expenses. The CRA defines eligible..."
         ↑ Context preserved!
```

### Chunk Size Trade-offs

| Chunk Size | Pros | Cons |
|------------|------|------|
| Small (256) | • More precise retrieval<br>• Better for specific facts | • Less context<br>• More chunks to store |
| Medium (512) | • Good balance<br>• Most common choice | • Medium context<br>• Medium precision |
| Large (1024) | • More context<br>• Fewer chunks | • Less precise<br>• May retrieve irrelevant info |

### Alternative Splitting Strategies

1. **Sentence-Based:**
   ```java
   // Split on sentence boundaries
   // Pro: Natural breaks, Con: Variable size
   ```

2. **Paragraph-Based:**
   ```java
   // Split on "\n\n"
   // Pro: Semantic units, Con: Very variable size
   ```

3. **Semantic Chunking:**
   ```java
   // Advanced: Split when topic changes
   // Pro: Coherent chunks, Con: Complex, slower
   ```

For most use cases, **token-based with overlap** (what we use) works best.

---

## Spring AI Architecture

### Component Hierarchy

```
┌─────────────────────────────────────┐
│   Spring Boot Application          │
│  (Dependency Injection, Config)    │
└────────────┬────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌─────────┐     ┌──────────┐
│  Chat   │     │ Embedding│
│ Model   │     │  Model   │
│ (Ollama)│     │ (Ollama) │
└────┬────┘     └────┬─────┘
     │               │
     │               ▼
     │         ┌──────────┐
     │         │  Vector  │
     │         │  Store   │
     │         └──────────┘
     │
     ▼
┌────────────┐
│ ChatClient │
│  (Fluent   │
│   API)     │
└────────────┘
```

### Key Interfaces

#### 1. EmbeddingModel
```java
public interface EmbeddingModel {
    List<Double> embed(String text);  // Text → Vector
}
```

**Implementations:**
- `OllamaEmbeddingModel` (our choice - local)
- `OpenAiEmbeddingModel` (cloud-based)
- `AzureOpenAiEmbeddingModel`

#### 2. VectorStore
```java
public interface VectorStore {
    void add(List<Document> documents);
    List<Document> similaritySearch(SearchRequest request);
}
```

**Implementations:**
- `SimpleVectorStore` (file-based, our choice)
- `ChromaVectorStore` (better for production)
- `PgVectorStore` (PostgreSQL-based)
- `PineconeVectorStore` (cloud-based)

#### 3. ChatModel
```java
public interface ChatModel {
    String call(String prompt);
}
```

**Implementations:**
- `OllamaChatModel` (our choice)
- `OpenAiChatModel`
- `AnthropicChatModel`

### Auto-Configuration Magic

Spring AI uses **auto-configuration** to set up beans:

```properties
# application.properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2
```

Spring AI automatically creates:
1. `OllamaChatModel` bean
2. `OllamaEmbeddingModel` bean
3. Connects them to your Ollama server

**You don't need to write:**
```java
// This is done automatically!
@Bean
public OllamaChatModel chatModel() {
    return new OllamaChatModel("http://localhost:11434", "llama3.2");
}
```

### ChatClient Fluent API

Modern, readable way to interact with LLMs:

```java
chatClient.prompt(prompt)           // Set prompt
    .system("You are a tax expert") // Add system message
    .user("What is TFSA?")          // Add user message
    .options(options)                // Set temperature, etc.
    .call()                          // Execute
    .content();                      // Get response
```

---

## Ollama Local LLMs

### Why Local LLMs?

**Advantages:**
- ✅ Free (no API costs)
- ✅ Private (data never leaves your machine)
- ✅ No rate limits
- ✅ Works offline
- ✅ Full control

**Disadvantages:**
- ❌ Requires powerful hardware (8GB+ RAM)
- ❌ Slower than cloud APIs
- ❌ Smaller models = less capable

### Ollama Architecture

```
┌──────────────┐
│ Your Spring  │
│    App       │
└──────┬───────┘
       │ HTTP
       ▼
┌──────────────┐
│   Ollama     │
│  Server      │
│ (port 11434) │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Model      │
│   Files      │
│ ~/.ollama/   │
└──────────────┘
```

### Model Sizes

| Model | Size | RAM Needed | Speed | Quality |
|-------|------|------------|-------|---------|
| llama3.2:1b | 1.3 GB | 4 GB | Fast | Basic |
| llama3.2:3b | 2 GB | 8 GB | Medium | Good |
| llama3.2 | 4.7 GB | 16 GB | Slower | Better |
| llama3.1:8b | 4.7 GB | 16 GB | Slower | Best |

### How Ollama Runs Models

1. **Model Loading:** Reads model from disk to RAM
2. **Tokenization:** Converts text → token IDs
3. **Inference:** Neural network processes tokens
4. **Decoding:** Converts token IDs → text
5. **Streaming:** Returns tokens one at a time

### GPU Acceleration

Ollama automatically uses GPU if available:

```bash
# Check GPU usage
ollama ps

# Should show GPU:0 if using GPU
```

**GPU vs CPU:**
- GPU: 10-100x faster
- CPU: Still works, just slower

---

## Common Challenges & Solutions

### Challenge 1: "No Relevant Documents Found"

**Symptoms:**
```json
{
  "answer": "I couldn't find any relevant information..."
}
```

**Causes & Solutions:**

1. **No documents ingested**
   ```bash
   # Solution: Run ingestion
   curl -X POST http://localhost:8080/api/ingest
   ```

2. **Similarity threshold too high**
   ```java
   // Change from 0.7 to 0.5
   .withSimilarityThreshold(0.5)
   ```

3. **Wrong embedding model**
   ```properties
   # Try different model
   spring.ai.ollama.embedding.model=all-minilm
   ```

4. **Question phrasing doesn't match documents**
   ```
   Instead of: "TFSA 2024 limit"
   Try: "What is the Tax-Free Savings Account contribution limit for 2024?"
   ```

### Challenge 2: Slow Responses

**Symptoms:** Taking 30+ seconds per query

**Solutions:**

1. **Use smaller model**
   ```bash
   ollama pull llama3.2:1b
   ```

2. **Reduce retrieved chunks**
   ```java
   .withTopK(3)  // Instead of 5
   ```

3. **Enable GPU** (if available)
    - Ollama uses GPU automatically
    - Check with `ollama ps`

4. **Reduce chunk size**
   ```java
   // Smaller context = faster processing
   TokenTextSplitter(256, 25, 5, 500, true)
   ```

### Challenge 3: Hallucinations

**Symptoms:** LLM makes up information not in documents

**Solutions:**

1. **Strengthen system prompt**
   ```java
   private static final String PROMPT_TEMPLATE = """
       ONLY use information from the provided context.
       If the answer is not in the context, say "I don't know".
       Never make up information.
       
       Context: {context}
       Question: {question}
       """;
   ```

2. **Lower temperature**
   ```properties
   # More deterministic
   spring.ai.ollama.chat.options.temperature=0.1
   ```

3. **Add citation requirement**
   ```java
   "Always cite the specific document and section where you found the information."
   ```

### Challenge 4: Out of Memory

**Symptoms:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solutions:**

1. **Increase JVM heap**
   ```bash
   # In pom.xml or command line
   mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx4g"
   ```

2. **Process documents in batches**
   ```java
   // In DocumentIngestionService
   List<Document> batch = new ArrayList<>();
   for (Document doc : allDocuments) {
       batch.add(doc);
       if (batch.size() >= 100) {
           vectorStore.add(batch);
           batch.clear();
       }
   }
   ```

3. **Use smaller chunk size**
   ```java
   TokenTextSplitter(256, 25, 5, 500, true)
   ```

### Challenge 5: Embedding Model Crashes

**Symptoms:**
```
Error: model not found: nomic-embed-text
```

**Solutions:**

1. **Pull the model**
   ```bash
   ollama pull nomic-embed-text
   ```

2. **Check Ollama is running**
   ```bash
   ollama list  # Should show models
   ```

3. **Try alternative model**
   ```properties
   spring.ai.ollama.embedding.model=all-minilm
   ```

---

## Performance Optimization Tips

### 1. Batch Processing

```java
// Bad: One at a time
for (Document doc : docs) {
    vectorStore.add(List.of(doc));  // Many API calls
}

// Good: Batch
vectorStore.add(docs);  // Single API call
```

### 2. Caching

```java
@Cacheable("rag-answers")
public String askQuestion(String question) {
    // Spring will cache responses
}
```

### 3. Async Processing

```java
@Async
public CompletableFuture<String> askQuestionAsync(String question) {
    return CompletableFuture.completedFuture(askQuestion(question));
}
```

### 4. Connection Pooling

```properties
# Reuse HTTP connections to Ollama
spring.ai.ollama.connection-timeout=30s
spring.ai.ollama.read-timeout=300s
```

---

## Next Learning Steps

1. **Experiment with parameters**
    - Try different chunk sizes
    - Adjust similarity thresholds
    - Test various models

2. **Add monitoring**
    - Log retrieval scores
    - Track response times
    - Monitor chunk usage

3. **Implement advanced features**
    - Conversation history
    - Multi-turn dialogue
    - Source citations
    - Confidence scores

4. **Explore alternatives**
    - Try Chroma DB for vector store
    - Test different embedding models
    - Experiment with hybrid search (vector + keyword)

5. **Build a UI**
    - Simple HTML chat interface
    - Show retrieved documents
    - Highlight sources

---

**Happy Learning! 🎓**