# Java Interview Coach

MVP: Spring Boot + LLM placeholder + simple chat API.

项目：Interview Coach — 面试陪练 agent
状态：
- 前端（Vercel）: 已部署 — https://interview-coach-alpha.vercel.app
- 后端: 暂未对外部署


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

## LLM Judge Config

The eval endpoint uses an LLM judge when an API key is available. Configure it with environment variables or `secrets.yml`:

```bash
setx LLM_OPENAI_API_KEY "your-api-key"
setx LLM_OPENAI_MODEL "glm-4.5-air"
setx LLM_OPENAI_ENDPOINT "https://open.bigmodel.cn/api/paas/v4/chat/completions"
```

Or create a local `secrets.yml` with:

```yaml
llm:
	openai:
		api-key: "your-api-key"
		model: "glm-4.5-air"
		endpoint: "https://open.bigmodel.cn/api/paas/v4/chat/completions"
```

If the key is missing, the eval still runs, but `judgeScore` stays empty and only the rule score is used.

## Deployment Layout

Local development and deployment now use different config layers:

- Local/dev: backend uses `application-dev.yml` with SQLite, frontend uses `frontend/.env.development`
- Production: backend uses `application-prod.yml`, frontend uses Vercel environment variables

For deployment, set these backend environment variables on Render:

- `JDBC_DATABASE_URL`
- `JDBC_DATABASE_USERNAME`
- `JDBC_DATABASE_PASSWORD`
- `LLM_OPENAI_API_KEY`
- `LLM_OPENAI_MODEL`
- `LLM_OPENAI_ENDPOINT`
- `APP_WEB_ALLOWED_ORIGINS`
- `RAG_KNOWLEDGE_PATH`

For the frontend on Vercel, set:

- `VITE_API_BASE` = your Render backend URL

Keep the Markdown knowledge base in a backend-readable directory or mounted volume. When it grows larger, move the retrieval index to a vector store.

Next: integrate Spring AI or OpenAI client, add vector search (Milvus/PGVector), implement RAG flow.