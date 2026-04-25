# 成都东软学院智能教案生成系统

项目目录：

- `backend`：Spring Boot 3 后端服务
- `frontend`：Vue 3 前端页面

当前已完成：

- MySQL 数据库连接
- DeepSeek API 教案生成
- 登录 / 退出
- 教案新建、生成、查看、编辑、保存
- 教案复制、删除
- Word 导出
- 本地开发启动脚本

## 环境要求

当前电脑环境：

- 系统默认 Java 11
- 已安装 JDK 21：`C:\Program Files\Java\jdk-21`
- Maven 3.9.9
- Node.js 22
- npm 11
- MySQL 已通过 Navicat 使用

后端使用 Spring Boot 3.3.x。Spring Boot 3 要求 Java 17 或以上，本项目启动脚本会临时使用本机已有的 JDK 21，不需要修改系统全局 Java。

## 后端启动

本地开发数据库密码和 DeepSeek API Key 已写入：

`backend/src/main/resources/application-local.yml`

该文件已被 `.gitignore` 忽略，只适合本机开发，不要用于线上服务器。

```powershell
cd E:\nsu-edu-maic\backend
.\start-backend.ps1
```

后端默认地址：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/api/health
```

## 前端启动

```powershell
cd E:\nsu-edu-maic\frontend
.\start-frontend.ps1
```

前端默认地址：

```text
http://localhost:5173
```

默认登录账号：

```text
admin / admin123456
teacher01 / teacher123456
```

## 数据库

数据库建表 SQL 位于：

`C:\Users\Again\Documents\Codex\2026-04-17-new-chat\nsu-edu-maic-schema.sql`

Navicat 操作说明：

`C:\Users\Again\Documents\Codex\2026-04-17-new-chat\navicat-database-guide.md`

## 使用说明

详细步骤见：

`E:\nsu-edu-maic\run-guide.md`

部署说明见：

`E:\nsu-edu-maic\deployment-guide.md`
