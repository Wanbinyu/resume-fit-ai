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
AI_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_MODEL=deepseek-chat
AI_REQUEST_TIMEOUT_MS=90000
```

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
        proxy_read_timeout 100s;
        proxy_send_timeout 100s;
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
