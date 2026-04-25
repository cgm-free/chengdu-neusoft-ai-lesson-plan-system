# 本地运行步骤

项目目录：

`E:\nsu-edu-maic`

## 1. 当前已经完成

- 数据库已在 Navicat 中创建：`nsu_edu_maic`
- 后端项目已创建：`E:\nsu-edu-maic\backend`
- 前端项目已创建：`E:\nsu-edu-maic\frontend`
- 后端 Maven 编译已通过
- 前端打包已通过
- 电脑已有 JDK 21：`C:\Program Files\Java\jdk-21`
- 后端使用 Spring Boot 3
- 已接入 DeepSeek API
- 已完成登录、教案生成、编辑保存、复制、删除、Word 导出

## 2. 启动后端

后端连接 MySQL 使用本地配置文件：

`E:\nsu-edu-maic\backend\src\main\resources\application-local.yml`

当前本地 MySQL 密码和 DeepSeek API Key 已配置在这个文件中。该文件已被 `.gitignore` 忽略，不要提交到 GitHub。

打开 PowerShell，执行：

```powershell
cd E:\nsu-edu-maic\backend
.\start-backend.ps1
```

启动成功后，浏览器访问：

```text
http://localhost:8080/api/health
```

## 3. 启动前端

另开一个 PowerShell 窗口，执行：

```powershell
cd E:\nsu-edu-maic\frontend
.\start-frontend.ps1
```

启动成功后，浏览器访问：

```text
http://localhost:5173
```

如果页面还是旧版本，按 `Ctrl + F5` 强制刷新。

## 4. 登录账号

开发阶段默认账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123456` |
| 教师 | `teacher01` | `teacher123456` |

如果数据库中的密码还是之前的占位符，后端启动时会自动修正为上面的默认密码。

## 5. 当前可用功能

教师端已经支持：

- 登录 / 退出
- 新建教案草稿
- 调用 DeepSeek 生成结构化教案
- 查看我的教案列表
- 点击历史教案查看详情
- 编辑学情分析、教学目标、重难点、课程思政、教学过程表、实践任务、作业、课后反思
- 保存修改
- 复制教案
- 删除教案
- 导出 Word 文档

## 6. 使用流程

1. 访问 `http://localhost:5173`。
2. 使用 `admin / admin123456` 登录。
3. 在左侧填写课程信息。
4. 点击“生成教案”。
5. 在中间编辑区修改内容。
6. 点击“保存修改”。
7. 点击“导出 Word”。
8. 在 Navicat 中查看 `lesson_plan`、`generation_record`、`lesson_plan_version`。

## 7. 后端接口

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/config/options`
- `GET /api/lesson-plans`
- `POST /api/lesson-plans`
- `POST /api/lesson-plans/generate`
- `GET /api/lesson-plans/{id}`
- `PUT /api/lesson-plans/{id}`
- `POST /api/lesson-plans/{id}/copy`
- `DELETE /api/lesson-plans/{id}`
- `GET /api/lesson-plans/{id}/export-word`

## 8. 常见问题

### 8.1 后端提示数据库连接失败

检查：

- MySQL 是否启动。
- Navicat 是否能连接 `本地MySQL`。
- 数据库名是否是 `nsu_edu_maic`。
- `application-local.yml` 里的数据库密码是否正确。

### 8.2 端口被占用

后端默认端口：

```text
8080
```

前端默认端口：

```text
5173
```

如果端口被占用，需要关闭占用端口的软件，或修改配置。

### 8.3 生成教案较慢

DeepSeek 生成完整教案一般需要几十秒。按钮显示加载状态时等待返回即可。

### 8.4 Word 导出失败

先确认当前选择的是已经生成过内容的教案。如果只是空草稿，导出的内容会比较少。

## 9. 后续可扩展功能

- PPT 大纲生成
- 学校 Word 模板上传
- 教案审核流程
- 多教师模板共享
- 课程资源库

## 10. 部署说明

部署到服务器的路线见：

`E:\nsu-edu-maic\deployment-guide.md`
