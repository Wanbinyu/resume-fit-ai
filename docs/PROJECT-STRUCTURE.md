# Resume Fit AI 项目整理说明

## 一、项目定位

Resume Fit AI 是一个面向 Java 后端和 AI Agent 岗位的简历优化平台。用户输入简历和目标 JD 后，系统完成匹配分析、证据提取、版本管理、报告生成和公开分享；管理员可以查看匿名使用数据和 AI 运行指标。

## 二、当前架构

```text
浏览器
  ├─ 用户端 :3000  Node.js + Express + 原生 HTML/CSS/JavaScript
  │    └─ 原有简历上传、JD 匹配、编辑和导出流程
  └─ 后端 :8080  Spring Boot 3 + Java 21
       ├─ Auth / JWT                 用户注册、登录和鉴权
       ├─ Resume / Version            简历、版本、发布和分享
       ├─ Job                         JD 保存与管理
       ├─ Analysis                    分析任务、进度、报告和历史记录
       ├─ AI Agent                    有界工具调用、Demo AI、真实模型适配
       ├─ Overview / Admin             公开统计和管理后台
       └─ H2 / MySQL                   开发内存库 / 生产关系型数据库
```

这是一个模块化单体（Modular Monolith），模块边界清晰，但部署仍是一个 Spring Boot 服务，适合当前项目规模和简历展示。

## 三、目录职责

| 目录 | 职责 |
| --- | --- |
| `public/` | 3000 端口用户端页面和前端逻辑 |
| `backend/src/main/java/.../auth` | 注册、登录、JWT |
| `backend/src/main/java/.../resume` | 简历、版本、分享访问统计 |
| `backend/src/main/java/.../job` | 岗位 JD |
| `backend/src/main/java/.../analysis` | 异步分析任务和报告 |
| `backend/src/main/java/.../ai` | Spring AI、Agent 工具链和用量记录 |
| `backend/src/main/java/.../admin` | 管理后台接口和管理员初始化 |
| `backend/src/main/resources/static/` | 后端公开首页、版本工作台、分享页、管理后台 |
| `backend/src/main/resources/schema.sql` | H2/MySQL 兼容的初始化表结构 |
| `test/` | Node.js 用户端测试 |
| `backend/src/test/` | Spring Boot 接口和 Agent 测试 |

## 四、页面入口

| 页面 | 地址 | 用途 |
| --- | --- | --- |
| 用户端 | `http://localhost:3000` | 原有简历优化流程 |
| 后端公开首页 | `http://localhost:8080/` | 平台介绍和匿名使用数据 |
| 版本工作台 | `http://localhost:8080/workspace.html` | 版本创建、JD 绑定、分析、对比、发布 |
| 分享页 | `http://localhost:8080/share.html` | 查看已发布简历版本 |
| 管理后台 | `http://localhost:8080/admin.html` | 运营指标、用户、任务和 Agent 轨迹 |
| Swagger | `http://localhost:8080/swagger-ui.html` | 调试和查看 API |

## 五、一次分析的核心链路

```text
创建分析任务
  → 持久化任务状态
  → Agent 提取 JD 关键词
  → Agent 匹配简历证据
  → Agent 计算覆盖率与风险
  → Demo AI / OpenAI-compatible 模型生成建议
  → 保存分析报告、用量和 Agent 轨迹
  → 前端轮询或 SSE 获取结果
```

Agent 默认最多执行 5 步，并带有无虚构事实约束。开发环境使用 Demo AI，不消耗 DeepSeek 额度；切换真实模型只需配置环境变量。

## 六、推荐启动方式

Windows 下双击根目录的 `启动全栈服务.cmd`。脚本优先使用 Maven 启动后端；如果本机没有 Maven，会自动使用已构建的 `backend/target/resume-fit-backend-0.1.0.jar`。也可以分别启动：

```bash
# 用户端
npm start

# 后端（另一个终端）
cd backend
mvn spring-boot:run
```

## 七、后续升级优先级

1. 完善用户端登录态，让 3000 端和版本工作台共享用户身份。
2. 增加简历版本 PDF/Word 导出，形成完整交付闭环。
3. 部署环境切换 MySQL，并补充任务幂等、重试和审计日志。
4. 最后再接入真实模型和精确 Token 统计，避免过早增加外部成本。
