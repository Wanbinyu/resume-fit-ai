# Resume Fit AI

一个可直接部署到海外服务器的简历 JD 匹配优化器 MVP。

当前项目已经扩展为“用户端 + Spring Boot 后端 + 简历版本工作台”的模块化单体，适合作为 Java 后端 / AI Agent 面试项目。项目结构、模块边界和分析链路见 [`docs/PROJECT-STRUCTURE.md`](./docs/PROJECT-STRUCTURE.md)。

## 本地运行

Windows 用户可以直接双击项目根目录中的：

```text
启动简历优化器.cmd
```

启动器会检查 Node.js、在首次运行时安装依赖，并在服务就绪后自动打开浏览器。保持启动器窗口开启；按 `Ctrl+C` 或关闭窗口即可停止服务。

如果需要同时启动前端和 Spring Boot 后端，可以双击 `启动全栈服务.cmd`。该脚本要求 Node.js 22.3+；检测到 Maven 3.9+ 时从源码启动，否则会自动使用已有的 `backend/target` 后端 jar。

也可以在终端手动运行：

```bash
npm install
npm start
```

打开：

```text
http://localhost:3000
```

没有配置 API Key 时，系统会进入演示模式，方便先看页面和流程。

## Spring Boot 后端

后端位于 [`backend/`](./backend/)，使用 Java 21、Spring Boot、MyBatis-Plus、MySQL/H2、JWT 和 Spring AI。
默认使用 H2 + Demo AI，可直接启动：

```bash
cd backend
mvn spring-boot:run
```

API 文档：`http://localhost:8080/swagger-ui.html`。正式使用 AI 时设置 `AI_DEMO_MODE=false`、`AI_API_KEY`、`AI_BASE_URL` 和 `AI_MODEL`。
打开 3000 端口的用户端后，顶部“版本工作台”会进入简历版本管理；入口地址由 `BACKEND_PUBLIC_URL` 配置。

## 当前功能

- 简历与岗位 JD 匹配评分、优势、缺口和关键词
- 两级联动职业筛选，服务端统一下发职业树并校验层级关系
- 应届生/社招身份与工作年限联动，按资历分层调整评分、反馈重点和简历栏目顺序
- 岗位定向改造：逐项目给出保留、强化、替换或删除决策，并提供具体功能、技术栈和验收证据
- 岗位不匹配时生成可执行的新项目方案、技能优先级和针对性面试问题
- 免费快速诊断只让 AI 生成评分、全部问题标题、2 个问题详解、2 条代表性改写、1 个项目诊断和少量面试准备；原简历排版预览由服务器直接生成，不等待 AI 重写
- 完整生成按目标岗位重写整份简历，并解锁全部问题详解、完整项目方案与面试题、求职信全文、简历编辑，以及 PDF、Word、TXT、Markdown 导出
- 完整报告要求 4-6 条不同内容的逐条改写，并以一页 A4 为最低目标，在不虚构事实的前提下展开职业概述、技能分组、工作和项目要点
- 求职身份选择为权威输入；若应届生简历明确声称多年工作经验，或社招简历明确写无工作经验，分析前会直接提示用户修正冲突
- 独立的完整版样例页使用固定虚构数据，不调用 AI、不覆盖用户输入，可直接查看所有标签、编辑状态和导出能力
- 经历改写建议、ATS 建议和求职信
- PDF、DOCX、TXT 简历上传与文字提取
- 简历最多 6000 字符，岗位 JD 最多 3000 字符
- AI 生成结构化简历草稿，保留技能分组、校园经历和其他原有栏目；免费版可查看完整排版，完整报告可直接修改
- 完整报告生成后自动检查原栏目和技术名词覆盖，必要时执行一次保真修复
- 经典/现代模板、主题色、字号和行距调整
- A4 实时预览，支持自然多页分页并通过浏览器导出 PDF
- 报告复制和浏览器本地历史记录
- 岗位定向建议支持导出为 Word DOCX、UTF-8 TXT 或 Markdown 文件
- AI 分析使用内存任务队列，支持进度轮询、刷新恢复、取消、并发控制和临时故障自动重试
- 原始简历与 JD 在任务结束后立即从任务内存清除，生成结果最多保留 15 分钟
- 匿名运行统计记录成功率、耗时和 token 用量，管理接口使用独立令牌保护
- 隐私政策、用户协议和分析前明确同意流程

## 配置 AI

复制环境变量文件：

```bash
cp .env.example .env
```

DeepSeek 示例：

```bash
AI_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_MODEL=deepseek-chat
```

任务与上线配置见 `.env.example` 和 [DEPLOY.md](./DEPLOY.md)。生产环境缺少 AI Key 时默认拒绝启动，避免误将演示模式公开上线。

Qwen 示例：

```bash
AI_PROVIDER=qwen
QWEN_API_KEY=sk-xxx
QWEN_MODEL=qwen-plus
```

OpenAI 示例：

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=sk-xxx
OPENAI_MODEL=gpt-4.1-mini
```

## 服务器部署

推荐最低配置：

```text
1 核 CPU / 1GB RAM / 20GB SSD
```

更稳配置：

```text
2 核 CPU / 2GB RAM / 40GB SSD
```

安装 Node.js 22.3+ 后：

```bash
npm install --omit=dev
npm run check
npm start
```

生产环境建议用 PM2：

```bash
npm install -g pm2
pm2 start server.js --name resume-fit-ai
pm2 save
```

Nginx 反向代理到 `http://127.0.0.1:3000` 即可。

## 安全

提示词注入、输出白名单和已知边界见 [SECURITY.md](./SECURITY.md)。
