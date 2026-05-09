# Interview Coach Web

A simple DeepSeek-style frontend for the Java interview coach backend.

## Tech Stack

- Vue 3
- Vite
- TypeScript

## Run

1. Start the backend in `D:\vscode笔记\interview-coach`:

```powershell
.\run-with-jdk17.bat
```

2. Start the frontend in `D:\vscode笔记\interview-coach\frontend`:

```powershell
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`, so the chat page can call `/api/v1/chat/ask` directly.

## Features

- Dark, polished chat UI
- Session-based interview profile form
- Chat history
- Quick prompts
- API integration with the Spring Boot backend
