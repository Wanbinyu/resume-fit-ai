# Resume Fit AI Backend

Spring Boot backend for the Resume Fit AI project. The first version is a modular monolith with a persisted analysis task model and a small Resume Optimization Agent.

本服务负责用户鉴权、简历版本管理、JD 管理、分析任务、AI Agent 轨迹、公开分享和管理统计。模块说明与面试讲解材料见根目录 [`docs/PROJECT-STRUCTURE.md`](../docs/PROJECT-STRUCTURE.md)。

工具注册、多轮 DeepSeek 调用、搜索配置和初学者讲解见 [`docs/AGENT-QUICKSTART.md`](../docs/AGENT-QUICKSTART.md)。

## Run locally

Requirements: Java 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

The default profile uses an in-memory H2 database and demo AI mode, so the API can start without an API key.

```text
http://localhost:8080
http://localhost:8080/swagger-ui.html
```

The public homepage at `http://localhost:8080/` displays aggregate usage data.
The management console is available at `http://localhost:8080/admin.html` after logging in with an administrator account.
The resume version workspace is available at `http://localhost:8080/workspace.html`.
Resume access can be recorded by calling `POST /api/public/resumes/{resumeId}/access?source=share`.
The endpoint records only the resume, source and timestamp; it does not store visitor IP or expose resume content.

To use a real OpenAI-compatible provider:

```bash
AI_DEMO_MODE=false
AI_API_KEY=your-key
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat
mvn spring-boot:run
```

For MySQL, activate the `mysql` profile and provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.

To create an administrator on first startup, set both `ADMIN_BOOTSTRAP_USERNAME` and
`ADMIN_BOOTSTRAP_PASSWORD`. The password is stored as a BCrypt hash; leaving either
variable empty disables bootstrap.

```bash
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=change-me-now
```

## Main API

工作台支持根据已保存版本生成纯文字中文 PDF，不调用语言模型。使用根目录 `启动Agent工作台.cmd` 启动；请通过 `RESUME_FONT_PATH` 配置支持中文的 TTF 字体，Windows 默认尝试黑体。无需 Python 或图片处理服务。部署与备份说明见 [`docs/AGENT-QUICKSTART.md`](../docs/AGENT-QUICKSTART.md)。

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/resumes/parse`
- `POST /api/resumes`
- `POST /api/jobs`
- `POST /api/analysis/tasks`
- `GET /api/analysis/tasks/{taskId}`
- `GET /api/analysis/tasks/{taskId}/stream`
- `DELETE /api/analysis/tasks/{taskId}`
- `GET /api/admin/dashboard`
- `GET /api/public/overview`
- `POST /api/public/resumes/{resumeId}/access`
- `GET /api/public/shares/{shareToken}`
- `GET /api/resumes/{resumeId}/versions`
- `POST /api/resumes/{resumeId}/versions`
- `PUT /api/resumes/{resumeId}/versions/{versionId}`
- `GET /api/resumes/{resumeId}/versions/compare?from={fromId}&to={toId}`
- `POST /api/resumes/{resumeId}/versions/{versionId}/publish`
- `GET /api/resumes/{resumeId}/reports`
- `GET /api/resumes/{resumeId}/versions/{versionId}/document?targetRole=`

## Design notes

- `@Async` and a database task state replace a message broker in v1.
- Files are parsed locally and are not uploaded to object storage.
- `Spring AI` uses the OpenAI-compatible model adapter, which supports DeepSeek, Qwen, and OpenAI-style endpoints.
- The model selects tools through an in-process registry; the application validates arguments, checks ownership, executes tools and returns results with original call IDs. Spring AI internal tool execution and retries are disabled. Actual model rounds, tool calls, time and token reservation are bounded.
- Demo mode runs the complete tool workflow locally without calling DeepSeek or consuming any model quota. Set `AI_DEMO_MODE=false` only when a real model is configured.
- The report shows actual model-reported token usage when available and a separate conservative token reservation. Missing usage and Demo usage are zero, not invented cost estimates. Search requires optional TAVILY_API_KEY; without it the task asks for a pasted JD. Drafts retain original text and require explicit user save.
