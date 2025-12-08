# 🍁 Canadian Tax RAG System

A **Retrieval Augmented Generation (RAG)** system built with **Spring AI** and **Ollama** for answering questions about Canadian tax law using your own documents.

## 🎯 What You'll Learn

This project teaches you:
- How to build a RAG system from scratch
- Spring AI framework and its components
- Working with local LLMs using Ollama
- Vector embeddings and similarity search
- Document processing and chunking strategies
- REST API design for AI applications

## 🏗️ Architecture

```
┌─────────────┐
│   User      │
│  Question   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│       REST API Controller           │
│  (TaxRagController.java)           │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│       RAG Service Layer             │
│  (TaxRagService.java)              │
│                                     │
│  1. Embed question                 │
│  2. Search vector store            │
│  3. Retrieve relevant chunks       │
│  4. Build prompt with context      │
│  5. Call LLM                       │
└──────┬────────────┬─────────────────┘
       │            │
       ▼            ▼
┌─────────────┐  ┌──────────────┐
│   Vector    │  │    Ollama    │
│   Store     │  │   (LLM)      │
│ (JSON file) │  │  llama3.2    │
└─────────────┘  └──────────────┘
```

## 📁 Project Structure

```
canadian-tax-rag/
├── src/main/java/com/example/rag/
│   ├── CanadianTaxRagApplication.java      # Main Spring Boot app
│   ├── config/
│   │   └── VectorStoreConfig.java          # Vector store setup
│   ├── service/
│   │   ├── DocumentIngestionService.java   # Loads & chunks documents
│   │   └── TaxRagService.java              # RAG query logic
│   └── controller/
│       └── TaxRagController.java           # REST endpoints
├── src/main/resources/
│   └── application.properties              # Configuration
├── data/
│   ├── tax-documents/                      # Your PDFs/TXT files
│   └── vector-store.json                   # Auto-generated embeddings
├── pom.xml                                  # Maven dependencies
└── README.md
```

## 🚀 Quick Start

### 1. Install Prerequisites

- **Java 17+**: [Download](https://adoptium.net/)
- **Maven 3.8+**: [Download](https://maven.apache.org/download.cgi)
- **Ollama**: [Download](https://ollama.ai/)

### 2. Download Ollama Models

```bash
# Chat model for generating answers (~2GB)
ollama pull llama3.2

# Embedding model for vectors (~300MB)
ollama pull nomic-embed-text

# Verify installation
ollama list
```

### 3. Clone/Setup Project

```bash
# Create project directory
mkdir canadian-tax-rag && cd canadian-tax-rag

# Copy all the code files provided
# (pom.xml, application.properties, Java classes)

# Create data directory
mkdir -p data/tax-documents
```

### 4. Add Your Tax Documents

Place PDF or TXT files in `data/tax-documents/`:
- CRA tax guides
- TFSA information sheets
- RRSP guides
- Provincial tax documents
- Any Canadian tax-related PDFs

### 5. Build & Run

```bash
# Build project
mvn clean install

# Run application
mvn spring-boot:run
```

Server starts at `http://localhost:8080`

### 6. Ingest Documents

```bash
# Load documents into vector store
curl -X POST http://localhost:8080/api/ingest

# Response shows number of chunks created
# {"message": "Successfully ingested 42 document chunks", "documentsIngested": 42}
```

### 7. Ask Questions!

```bash
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the TFSA contribution limit for 2024?"}'
```

## 📚 How It Works

### The RAG Pipeline

1. **Document Ingestion** (`DocumentIngestionService`)
    - Reads PDFs/TXT from `data/tax-documents/`
    - Splits into 512-token chunks with 50-token overlap
    - Generates embeddings using `nomic-embed-text`
    - Stores in `SimpleVectorStore` (JSON file)

2. **Question Processing** (`TaxRagService`)
    - User asks: "What is the TFSA limit?"
    - Question converted to embedding vector
    - Vector store searched for similar chunks
    - Top 5 most similar chunks retrieved (>0.7 similarity)

3. **Answer Generation**
    - Retrieved chunks combined into context
    - Prompt created: `[Context] + [Question]`
    - Sent to `llama3.2` via Ollama
    - AI generates answer based on documents

### Key Concepts

**Embeddings**: Numbers that represent text meaning
- `nomic-embed-text` converts text → 768-dimensional vector
- Similar text = similar vectors (measured by cosine similarity)
- Enables semantic search (meaning-based, not keyword)

**Vector Store**: Database of embeddings
- `SimpleVectorStore`: File-based, saved as JSON
- Performs similarity search using cosine distance
- Alternative: Chroma DB (better for large datasets)

**Text Splitting**: Breaking documents into chunks
- Why? LLMs have token limits (~8K tokens)
- Chunk size: 512 tokens (balance between context and precision)
- Overlap: 50 tokens (prevents losing context at boundaries)

**RAG Benefits**:
- ✅ Up-to-date information (your documents)
- ✅ Domain-specific knowledge
- ✅ Reduces hallucinations (grounded in facts)
- ✅ Cites sources

## 🎛️ API Endpoints

### POST `/api/ingest`
Loads all documents from `data/tax-documents/`

**Response:**
```json
{
  "message": "Successfully ingested 42 document chunks",
  "documentsIngested": 42
}
```

### POST `/api/ask`
Ask a question about Canadian taxes

**Request:**
```json
{
  "question": "What expenses can I deduct as a small business?"
}
```

**Response:**
```json
{
  "question": "What expenses can I deduct as a small business?",
  "answer": "According to the CRA guidelines, small businesses can deduct..."
}
```

### GET `/api/retrieve?question=TFSA`
Debug endpoint - see retrieved documents

**Response:**
```json
{
  "question": "TFSA",
  "documents": [
    {
      "content": "The Tax-Free Savings Account (TFSA)...",
      "source": "cra-tfsa-guide.pdf",
      "similarity": 0.89
    }
  ]
}
```

### GET `/api/health`
Health check

## ⚙️ Configuration

### `application.properties`

```properties
# Ollama server
spring.ai.ollama.base-url=http://localhost:11434

# Chat model (answer generation)
spring.ai.ollama.chat.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.2  # Lower = more focused

# Embedding model (vectorization)
spring.ai.ollama.embedding.model=nomic-embed-text

# Storage paths
vector.store.file-path=./data/vector-store.json
documents.path=./data/tax-documents
```

### Changing Models

```bash
# Faster but less accurate
ollama pull llama3.2:1b

# More accurate but slower
ollama pull llama3.1:8b

# Different embedding model
ollama pull all-minilm
```

Update `application.properties` to use new models.

## 🔧 Customization

### Adjust Chunk Size

In `DocumentIngestionService.java`:

```java
// Larger chunks = more context per chunk
TokenTextSplitter splitter = new TokenTextSplitter(
    1024,  // chunk size
    100,   // overlap
    5,     // min chunk size
    2000,  // max chunk size
    true   // keep separator
);
```

### Change Similarity Threshold

In `TaxRagService.java`:

```java
SearchRequest.query(question)
    .withTopK(5)
    .withSimilarityThreshold(0.6)  // Lower = more results
```

### Modify Prompt Template

In `TaxRagService.java`, customize the `PROMPT_TEMPLATE`:

```java
private static final String PROMPT_TEMPLATE = """
    You are a tax expert specializing in Canadian law.
    Always cite the specific document when answering.
    
    Context: {context}
    Question: {question}
    Answer:
    """;
```

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| `Connection refused localhost:11434` | Start Ollama: `ollama serve` |
| `Model not found` | Download models: `ollama pull llama3.2` |
| `No relevant documents found` | 1. Run `/api/ingest`<br>2. Check files in `data/tax-documents/`<br>3. Lower similarity threshold |
| Slow responses | Use smaller model: `ollama pull llama3.2:1b` |
| Out of memory | Reduce chunk size or use fewer documents |

## 📖 Learning Resources

- [Spring AI Docs](https://docs.spring.io/spring-ai/reference/)
- [Ollama Documentation](https://ollama.ai/docs)
- [RAG Explained](https://www.promptingguide.ai/techniques/rag)
- [Vector Embeddings Guide](https://www.pinecone.io/learn/vector-embeddings/)

## 🎯 Next Steps

### Beginner Enhancements
1. Add more documents to improve coverage
2. Experiment with different models
3. Adjust temperature for different response styles
4. Try different similarity thresholds

### Intermediate Enhancements
1. **Add source citations** - Return which documents were used
2. **Implement conversation history** - Multi-turn conversations
3. **Add metadata filtering** - Filter by document type, date, etc.
4. **Build a simple web UI** - HTML + JavaScript frontend

### Advanced Enhancements
1. **Use Chroma DB** - Better performance for large datasets
2. **Implement hybrid search** - Combine vector + keyword search
3. **Add re-ranking** - Improve retrieval accuracy
4. **Streaming responses** - Real-time answer generation
5. **Multi-modal RAG** - Support images, tables from PDFs

## 🤝 Contributing Ideas

- Support for French language tax documents
- Provincial tax specialization
- Tax calculation tools
- Form filling assistance
- Historical tax rate comparisons

## 📝 License

MIT License - Feel free to use for learning and commercial projects!

---

**Built with ❤️ using Spring AI and Ollama**