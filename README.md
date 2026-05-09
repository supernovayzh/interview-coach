# Java Interview Coach

MVP: Spring Boot + LLM placeholder + simple chat API.

## Features

- LLM interview chat orchestration
- Local Markdown RAG (`search` tool)
- Scoring / follow-up / session summary tools
- Web search tool (`web_search`) for online evidence and reference answer synthesis

Quick start:

1. Build and run with Maven:

```bash
mvn spring-boot:run
```

2. Example endpoint:
POST http://localhost:8080/api/v1/chat/ask
Body: { "question": "Explain Redis cache penetration" }

## Web Search Tool Config

`web_search` is disabled by default. Configure in `application.yml` or `secrets.yml`:

```yaml
web:
	search:
		enabled: true
		api-key: "your-serpapi-key"
		endpoint: "https://serpapi.com/search.json"
		engine: "google"
		timeout-seconds: 12
		default-top-k: 5
```

When planner decides to use `web_search`, it can pass step arguments:

- `query`: custom query string
- `topK`: number of results
- `domains`: optional allowlist domains (e.g., `oracle.com`, `spring.io`)

Next: integrate Spring AI or OpenAI client, add vector search (Milvus/PGVector), implement RAG flow.