# 海外服务器部署

以下命令以 Ubuntu 22.04/24.04 为例，建议使用 Node.js 22 LTS。

## 1. 安装 Node.js

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs nginx
node -v
npm -v
```

## 2. 上传项目并安装依赖

```bash
cd /var/www
sudo mkdir resume-fit-ai
sudo chown -R $USER:$USER resume-fit-ai
cd resume-fit-ai

npm install --omit=dev
cp .env.example .env
nano .env
chmod 600 .env
```

`.env` 至少配置一个模型：

```bash
NODE_ENV=production
AI_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_MODEL=deepseek-chat
AI_REQUEST_TIMEOUT_MS=120000
AI_MAX_RETRIES=1
AI_MAX_CONCURRENCY=2
AI_MAX_QUEUE_SIZE=20
ANALYZE_RATE_LIMIT=10
AI_DAILY_TASK_LIMIT=200
ANALYSIS_TASK_TTL_MS=900000
ALLOW_DEMO_MODE=false
ADMIN_STATS_TOKEN=使用_openssl_rand_hex_32_生成
SITE_OPERATOR_NAME=实际运营主体或个人姓名
SITE_CONTACT=客服邮箱或以_https_开头的联系页面
```

生成管理统计令牌：

```bash
openssl rand -hex 32
```

生产环境缺少有效 AI 配置时会拒绝启动。只有明确设置 `ALLOW_DEMO_MODE=true` 才允许以演示模式运行。

## 3. 用 PM2 常驻运行

```bash
sudo npm install -g pm2
pm2 start ecosystem.config.cjs
pm2 save
pm2 startup
```

## 4. 配置 Nginx

```bash
sudo nano /etc/nginx/sites-available/resume-fit-ai
```

写入：

```nginx
server {
    listen 80;
    server_name your-domain.com;
    client_max_body_size 6m;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_connect_timeout 10s;
        proxy_read_timeout 30s;
        proxy_send_timeout 30s;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用：

```bash
sudo ln -s /etc/nginx/sites-available/resume-fit-ai /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 5. HTTPS

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

## 6. 上线检查

```bash
curl https://your-domain.com/api/health
pm2 logs resume-fit-ai --lines 50
```

健康接口应返回 `"aiConnected":true`。不要把 `.env` 上传到公开仓库，也不要在前端代码中放 API Key。

查看匿名运行统计：

```bash
curl -H "Authorization: Bearer $ADMIN_STATS_TOKEN" https://your-domain.com/api/admin/stats
```

统计接口只返回任务数、状态、耗时和 AI token 用量，不包含简历或岗位内容。`ADMIN_STATS_TOKEN` 未配置时接口保持关闭。

`ANALYZE_RATE_LIMIT` 是单 IP 每 10 分钟最多创建的分析请求数；`AI_DAILY_TASK_LIMIT` 是单进程每日最多接受的真实 AI 任务数。每日上限用于防止异常消耗，但服务器重启会重置内存计数，仍应在 AI 服务商后台设置余额上限和费用告警。

继续检查：

```bash
npm run check
npm test
npm run security
curl https://your-domain.com/privacy.html
curl https://your-domain.com/terms.html
```

生产环境缺少 `SITE_OPERATOR_NAME` 或 `SITE_CONTACT` 时会拒绝启动，防止法律页面遗漏运营主体和联系方式。仍应根据服务器所在地、服务对象所在地和所选 AI 服务商的条款完成合规复核。
