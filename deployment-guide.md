# 部署说明

本项目当前是本地开发版。正式给老师使用时，建议优先部署到国内云服务器，不建议把 Cloudflare Pages 作为正式入口。

## 1. 推荐部署路线

阶段一：本地运行

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`
- 数据库：本地 MySQL `nsu_edu_maic`

阶段二：服务器 IP 演示

- 购买腾讯云 / 阿里云 / 华为云轻量服务器
- 安装 JDK 21、Node.js、MySQL、Nginx
- 用服务器公网 IP 访问
- 暂时不绑定域名

阶段三：域名正式访问

- 域名：`nsu-edu-maic.xyz`
- 完成域名实名认证
- 如果服务器在中国大陆，完成 ICP 备案
- 配置 Nginx 和 HTTPS

## 2. 服务器目录建议

```text
/opt/nsu-edu-maic
  backend/
  frontend/
  logs/
```

## 3. 后端生产配置

不要在生产环境使用本地的 `application-local.yml`。

生产环境建议用环境变量：

```bash
export DB_PASSWORD="数据库强密码"
export AI_API_KEY="DeepSeek API Key"
export AI_BASE_URL="https://api.deepseek.com"
export REGISTRATION_INVITATION_CODE="校内邀请码"
```

后端启动：

```bash
java -jar nsu-edu-maic-backend-0.0.1-SNAPSHOT.jar
```

## 4. 前端生产构建

```bash
cd frontend
npm install
npm run build
```

构建结果：

```text
frontend/dist
```

把 `dist` 放到 Nginx 静态目录。

## 5. Nginx 示例

```nginx
server {
    listen 80;
    server_name nsu-edu-maic.xyz;

    root /opt/nsu-edu-maic/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 6. Cloudflare 说明

Cloudflare 可以用来管理 DNS，但如果主要用户在中国大陆，普通 Cloudflare Pages / Workers 不建议作为正式部署方案。

更稳的做法：

- DNS 可以放 Cloudflare
- 正式服务部署到国内云服务器
- 域名解析到国内服务器公网 IP
- 完成备案后配置 HTTPS

## 7. 上线前必须修改

- 修改默认管理员密码
- 使用强数据库密码
- 替换 DeepSeek API Key
- 不提交 `application-local.yml`
- 不把 API Key 写到前端
- 配置 HTTPS
