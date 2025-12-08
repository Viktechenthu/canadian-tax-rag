# Canadian Tax RAG System - Complete Setup Guide

## Prerequisites

1. **Java 17+** - [Download](https://adoptium.net/)
2. **Maven 3.8+** - [Download](https://maven.apache.org/download.cgi)
3. **Ollama** - [Download](https://ollama.ai/)

## Step 1: Install and Configure Ollama

Ollama is a tool that runs large language models locally on your machine.

### Install Ollama

```bash
# macOS/Linux
curl -fsSL https://ollama.ai/install.sh | sh

# Windows: Download from https://ollama.ai/download
```

### Download Required Models

```bash
# Chat model (for generating answers) - ~2GB
ollama pull llama3.2

# Embedding model (for creating vectors) - ~300MB
ollama pull nomic-embed-text
```

### Verify Ollama is Running

```bash
# Check if Ollama is running (should return list of models)
ollama list

# Test the chat model
ollama run llama3.2 "Hello, how are you?"
```

Ollama runs on `http://localhost:11434` by default.

## Step 2: Project Structure

Create this directory structure:

```
canadian-tax-rag/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── rag/
│       │               ├── CanadianTaxRagApplication.java
│       │               ├── config/
│       │               │   └── VectorStoreConfig.java
│       │               ├── service/
│       │               │   ├── DocumentIngestionService.java
│       │               │   └── TaxRagService.java
│       │               └── controller/
│       │                   └── TaxRagController.java
│       └── resources/
│           └── application.properties
├── data/
│   ├── tax-documents/    # Place your PDFs/TXT files here
│   └── vector-store.json # Auto-generated
└── pom.xml
```

## Step 3: Add Canadian Tax Documents

Place your Canadian tax documents in `data/tax-documents/`:

```bash
mkdir -p data/tax-documents
```

**Example documents to add:**
- CRA T1 General Guide PDF
- TFSA information sheets
- RRSP contribution guides
- Tax treaty documents
- Provincial tax guides

**Supported formats:** PDF, TXT

## Step 4: Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Step 5: Ingest Documents

Before asking questions, you need to load documents into the vector store:

```bash
# Using curl
curl -X POST http://localhost:8080/api/ingest

# Expected response:
# {
#   "message": "Successfully ingested 42 document chunks",
#   "documentsIngested": 42
# }
```

**What happens during ingestion:**
1. Reads all PDF/TXT files from `data/tax-documents/`
2. Splits them into 512-token chunks (with 50-token overlap)
3. Generates embeddings using `nomic-embed-text` model
4. Stores embeddings in `data/vector-store.json`

## Step 6: Ask Questions

```bash
# Ask a question
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the TFSA contribution limit for 2024?"}'

# Expected response:
# {
#   "question": "What is the TFSA contribution limit for 2024?",
#   "answer": "According to the tax documents, the TFSA contribution limit for 2024 is $7,000..."
# }
```

## Step 7: Debug Retrieved Documents

See what documents the system is using to answer questions:

```bash
curl "http://localhost:8080/api/retrieve?question=TFSA%20limits"
```

This returns the actual document chunks that matched your query.

## Understanding the Flow

### How RAG Works in This System

```
User Question
     ↓
1. Convert question to embedding (vector) using nomic-embed-text
     ↓
2. Search vector store for similar document chunks
     ↓
3. Retrieve top 5 most similar chunks (similarity > 0.7)
     ↓
4. Combine chunks into context
     ↓
5. Send [Context + Question] to llama3.2
     ↓
6. Return generated answer
```

### Key Components Explained

**EmbeddingModel:**
- Converts text to vectors (arrays of numbers)
- Similar text = similar vectors
- Enables semantic search (meaning-based, not keyword-based)

**VectorStore:**
- Database of document embeddings
- Performs similarity search using cosine distance
- SimpleVectorStore saves to JSON file

**TextSplitter:**
- Breaks documents into chunks
- Default: 512 tokens per chunk, 50 token overlap
- Overlap prevents losing context at boundaries

**ChatClient:**
- Fluent API for interacting with LLM
- Handles prompt formatting
- Streams responses from Ollama

## Troubleshooting

### Issue: "Connection refused to localhost:11434"
**Solution:** Ollama isn't running. Start it with `ollama serve`

### Issue: "Model not found"
**Solution:** Download models: `ollama pull llama3.2` and `ollama pull nomic-embed-text`

### Issue: "No relevant documents found"
**Solution:**
1. Run ingestion: `POST /api/ingest`
2. Check documents exist in `data/tax-documents/`
3. Lower similarity threshold in code (currently 0.7)

### Issue: Slow responses
**Solution:**
- Smaller model: `ollama pull llama3.2:1b` (faster, less accurate)
- Reduce topK (retrieve fewer documents)
- Use GPU acceleration if available

## Configuration Options

### Change Models

In `application.properties`:

```properties
# Use different chat model
spring.ai.ollama.chat.model=mistral

# Use different embedding model  
spring.ai.ollama.embedding.model=all-minilm
```

### Adjust Response Temperature

Lower = more focused, higher = more creative:

```properties
spring.ai.ollama.chat.options.temperature=0.2  # Conservative
spring.ai.ollama.chat.options.temperature=0.7  # Balanced
```

### Change Chunk Size

In `DocumentIngestionService.java`:

```java
// Larger chunks = more context, fewer chunks
TokenTextSplitter splitter = new TokenTextSplitter(1024, 100, 5, 2000, true);

// Smaller chunks = more precise retrieval, more chunks
TokenTextSplitter splitter = new TokenTextSplitter(256, 25, 5, 500, true);
```

## Next Steps

### Enhancements to Try

1. **Add metadata filtering:**
   ```java
   SearchRequest.query(question)
       .withTopK(5)
       .withFilterExpression("source == 'CRA-Guide-2024.pdf'")
   ```

2. **Use Chroma DB instead of SimpleVectorStore:**
    - More efficient for large datasets
    - Better query performance
    - Add dependency: `spring-ai-chroma-store`

3. **Add citation tracking:**
    - Return source documents with answers
    - Show which pages information came from

4. **Implement hybrid search:**
    - Combine vector search with keyword search
    - Better for specific terms (like tax codes)

5. **Add streaming responses:**
   ```java
   return chatClient.prompt(prompt)
       .stream()
       .content();
   ```

6. **Build a frontend:**
    - React/Vue/Angular interface
    - Chat-style UI
    - Document upload capability

## Learning Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Ollama Documentation](https://ollama.ai/docs)
- [Understanding Vector Embeddings](https://www.pinecone.io/learn/vector-embeddings/)
- [RAG Explained](https://www.promptingguide.ai/techniques/rag)

## Example Questions to Try

- "What is the TFSA contribution limit?"
- "How do I calculate capital gains tax in Canada?"
- "What are the RRSP contribution deadlines?"
- "Explain the difference between federal and provincial tax credits"
- "What expenses can I deduct as a small business owner?"