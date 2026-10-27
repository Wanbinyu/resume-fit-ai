# 代码阅读与修改位置

本指南对应 Java 工作台。根目录 `server.js` 和 `public/` 是 Node 旧版流程，两套分析入口目前并存。
Java 源码根目录为 `backend/src/main/java/com/wanbinyu/resumefit/`，各业务包的 `package-info.java` 说明模块职责。

## 一、模块如何配合

```text
浏览器 workspace.html
  → security：从 JWT 建立用户身份
  → Controller：接收请求、校验接口参数、提取当前用户
  → Service：检查资源归属、执行业务规则
  → Mapper / Entity：数据库读写

其中，创建分析任务还会经过：
AnalysisTaskService：事务内保存 PENDING，提交后派发
  → AnalysisWorker：领取任务，绑定用户权限、取消检查和进度回调
  → ResumeOptimizationAgent：模型循环、工具执行和预算限制
  → AnalysisWorker：保存最终任务状态、用量和成功报告
```

| 包 | 职责 |
| --- | --- |
| `auth` | 注册、密码验证和登录令牌签发 |
| `security` | JWT 解析、公开接口及管理员接口权限规则 |
| `resume` | 初始简历、文件文字提取、版本、分享和 PDF 导出 |
| `job` | 用户保存的岗位 JD 及其所有权检查 |
| `analysis` | 数据库任务、异步执行、取消、进度和历史报告 |
| `ai` | 模型适配、工具注册、多轮循环、搜索和用量 |
| `admin` | 管理员初始化、运营统计和任务详情 |
| `overview` | 公开聚合统计 |
| `common` | 统一响应、异常转换和用户 ID 提取 |
| `user` | 用户实体和数据库访问 |
| `system` | 服务健康检查 |

## 二、先分清简历、版本、任务和报告

- `ResumeEntity`：创建或导入时保存的初始简历。当前分析任务根据 `resumeId` 读取它。
- `ResumeVersionEntity`：用户保存的具体版本，可编辑、对比和发布。创建初始简历时同时生成原始版本。
- `AnalysisTaskEntity`：一次分析请求的输入、状态、进度和结果；运行过程中结果字段也可保存工具轨迹。
- `AnalysisReportEntity`：成功分析生成的历史报告。查看报告不重新调用模型，也不自动保存简历版本。

当前任务并非按版本 ID 分析。修改了版本内容，不意味着初始简历内容会同步改变。
工作台中的“载入草稿”只填写编辑器，用户点击保存才写入版本；发布也是独立操作。

## 三、建议阅读顺序

1. `analysis/AnalysisController.java`：从 HTTP 请求进入分析业务。
2. `analysis/AnalysisTaskService.java`：归属校验、创建任务、事务提交后派发、取消与 SSE。
3. `analysis/AnalysisWorker.java`：领取任务、创建 `AgentRun`、保存结果。
4. `ai/AgentRun.java`：每任务独立的上下文及数据读取函数。
5. `ai/AgentToolRegistry.java`：工具列表、参数格式、实际执行和错误返回。
6. `ai/ResumeOptimizationAgent.java`：模型消息、执行限制、工具回传和报告组装。
7. `ai/SpringAiProvider.java`：一轮真实模型请求；工具由外层执行。
8. `resume/ResumeVersionService.java`：用户确认后的版本保存、分享与对比。
9. `resume/ResumeDocumentService.java`：已保存版本的中文 PDF 排版和分页。

## 四、新增工具在哪里改

工具保存在 `AgentToolRegistry` 的内存 `Map<String, Entry>` 中，不从数据库加载。
每次任务创建注册表时，构造方法中的 `register(...)` 都会执行。

1. 在独立业务类实现实际能力，再在注册表构造方法中添加 `register(...)`。
2. 决定开放模式：`resumeOnly` 分支之前的工具两种模式都可用；分支内部仅供体检；分支之后仅供岗位分析。
3. 修改对应提示词，说明调用条件和结果用途。必需步骤还应增加程序完成条件检查。
4. 需要独立展示结果时，在 `AgentRun` 保存结果，修改报告组装及工作台渲染。
5. Demo 按代码预设步骤调用工具，新增工具不会自动加入 Demo；需要同步修改 `DemoAiProvider`。
6. 验证正常调用、错误参数、前置条件、权限、call ID 回传和预算限制。

当前注册器仅支持必填字符串参数，长度 1–80。需要可选参数、数字或数组时，要同时修改 schema 构造和执行校验。
不要只修改给模型看的参数说明。

## 五、常见修改入口

| 要修改什么 | 入口 |
| --- | --- |
| 提示词、模型报告格式及程序完成条件 | `ai/ResumeOptimizationAgent.java` |
| 工具列表、分发、参数校验 | `ai/AgentToolRegistry.java` |
| 关键词表和文字覆盖率 | `ai/ResumeAgentTools.java` |
| 搜索服务适配 | `ai/JobSearchProvider.java` 及实现类 |
| 模型选择和接入 | `AiProvider`、`AiProviderConfig`、本地配置 |
| 轮次、时间和 token 预留 | `AiProperties`、`application.yml` 及本地 `.env` |
| 任务状态、取消和 SSE | `analysis/AnalysisTaskService.java` |
| 异步执行和结果入库 | `analysis/AnalysisWorker.java` |
| 简历版本保存、发布、对比 | `resume/ResumeVersionService.java` |
| 文件文字提取与长度限制 | `resume/FileTextExtractor.java` |
| PDF 字体、分页和排版 | `resume/ResumeDocumentService.java` |
| JWT 及接口访问规则 | `security/` |
| 页面操作、轮询和结果显示 | `backend/src/main/resources/static/workspace.html` |

## 六、阅读时需要知道的边界

- JWT 建立用户身份，Service 的所有权查询限制具体数据；两者不能互相替代。
- 工作台目前轮询任务接口。后端也提供 SSE，每 500 毫秒读取任务状态，不是模型逐 token 推送。
- 任务采用线程池和数据库状态，没有进程重启后的任务恢复机制。
- token 预留是应用估算；模型报告的实际用量单独记录，不代表精确费用控制。
- 版本对比按去重后的文本行统计增删，不是字符级 diff。
- 导出读取已保存的版本，不调用模型，不复刻原文件版式。

## 七、验证与维护

```powershell
mvn -f backend/pom.xml test
npm run check
npm test
```

普通测试使用离线模型和测试数据库。真实 DeepSeek 测试需要显式设置 `RF_REAL_MODEL_TEST=true`，会消耗模型额度。
本地 API 密钥保存在已忽略的 `.env` 中，不写入源码或提交记录。

整理代码时保留现有命名和行为，优先展开压缩语句、统一缩进，并解释事务、状态和权限的原因。
简单字段和 Mapper 不必逐行注释。需要提取方法或拆分通用 Agent 核心时，另做可验证的结构调整。
