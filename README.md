# Java Interview Coach

MVP: Spring Boot + LLM placeholder + simple chat API.

Quick start:

1. Build and run with Maven:

```bash
mvn spring-boot:run
```

2. Example endpoint:
POST http://localhost:8080/api/v1/chat/ask
Body: { "question": "Explain Redis cache penetration" }

Next: integrate Spring AI or OpenAI client, add vector search (Milvus/PGVector), implement RAG flow.