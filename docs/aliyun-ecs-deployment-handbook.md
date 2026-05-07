# 成都东软学院智能教案生成系统：阿里云 ECS 零基础部署文档

本文是本项目从“申请阿里云 ECS 试用服务器”到“公网 IP 访问、域名访问、HTTPS 访问”的完整部署记录。写法按零基础理解设计：先解释概念，再给命令，再说明命令作用和成功标志。

当前线上入口：

```text
https://nsu-lesson.xyz/login
https://www.nsu-lesson.xyz/login
http://8.137.148.233/login
```

当前服务器：

```text
公网 IP：8.137.148.233
域名：nsu-lesson.xyz
系统：Ubuntu 22.04 64 位
部署目录：/opt/chengdu-neusoft-ai-lesson-plan-system
后端服务名：nsu-maic-backend
Nginx 配置：/etc/nginx/sites-available/nsu-maic
后端环境变量：/etc/nsu-maic-backend.env
```

注意：本文不会写出真实数据库密码和真实大模型 API Key。真实密钥只保存在服务器环境变量文件中，不能提交到 GitHub。

## 1. 先理解这套系统部署后由哪些东西组成

本项目不是一个单纯网页。它部署后由 5 个主要部分组成：

| 部分 | 作用 | 本次部署位置 |
|---|---|---|
| Vue 前端 | 用户看到的网页，例如登录页、新建教案、教案管理 | Nginx 静态目录 |
| Spring Boot 后端 | 登录、上传材料、调用大模型、生成 Word/PDF | systemd 服务 |
| MySQL | 保存用户、教案记录、上传材料、生成任务 | ECS 本机 MySQL |
| LibreOffice | 把 Word 转成 PDF | ECS 本机命令行工具 |
| Nginx | 对外提供网站访问，把 `/api` 转给后端 | ECS 本机 Nginx |

访问流程是：

```text
浏览器
  -> https://nsu-lesson.xyz
  -> Nginx
  -> 前端静态页面
  -> /api 请求由 Nginx 转发到 Spring Boot 后端
  -> 后端读写 MySQL、调用 DeepSeek、使用 LibreOffice 转 PDF
```

为什么不直接访问后端 8080：

```text
8080 只给服务器内部使用。
用户只访问 80/443，由 Nginx 统一入口处理。
```

## 2. 阿里云 ECS 是什么，为什么选它

ECS 可以理解为“云上的一台 Linux 电脑”。你可以通过 SSH 登录它，在里面安装 Java、MySQL、Nginx，然后运行项目。

本次使用的是阿里云 ECS 免费试用个人版：

```text
地域：西南 1（成都）
公网 IP：8.137.148.233
操作系统：Ubuntu 22.04 64 位
规格：4 vCPU / 8 GiB
系统盘：ESSD Entry 40 GiB
公网带宽：100 Mbps 峰值
```

为什么选成都：

```text
你和学校用户主要在中国大陆，成都节点对四川/西南访问更近。
域名备案后，国内服务器访问速度通常比香港/海外更稳定。
```

为什么 4 vCPU / 8 GiB 合适：

```text
Java 后端 + MySQL + LibreOffice 比普通静态网站更吃内存。
2C4G 可以跑，但生成 Word/PDF 和大文件解析时更容易吃紧。
4C8G 更适合现在这种“课程标准 + PPT + 教学日历 + Word/PDF 导出”的系统。
```

40 GiB 系统盘够不够：

```text
演示和小范围试用：够。
长期保存大量 PPT、PDF、DOCX：不够稳。
正式使用建议后续接入阿里云 OSS，把上传文件放 OSS，MySQL 只存文件地址。
```

## 3. 购买/试用 ECS 时怎么选

你当时看到两个入口：

```text
轻量应用服务器 2vCPU 4GiB
云服务器 ECS 免费试用个人版
```

最后实际使用的是 ECS 免费试用个人版，而不是轻量应用服务器。

原因：

```text
ECS 更接近正式生产环境，后续安装 MySQL、Nginx、Java、LibreOffice 更直接。
轻量应用服务器适合 WordPress/简单网站，后续扩展、迁移和排障不如 ECS 清晰。
```

ECS 选择建议：

```text
地域：西南 1（成都）
系统：Ubuntu 22.04 64 位
规格：4 vCPU / 8 GiB
系统盘：40 GiB 起步
公网 IP：需要
安全组：放行 22、80、443
```

## 4. 安全组是什么，为什么要放行 80 和 443

安全组可以理解为阿里云外层防火墙。即使服务器里 Nginx 正常监听 80，如果安全组没有放行 80，公网也访问不了。

本次已放行：

| 端口 | 协议 | 用途 |
|---|---|---|
| 22 | TCP | SSH 远程登录服务器 |
| 80 | TCP | HTTP 访问、Let's Encrypt 证书验证 |
| 443 | TCP | HTTPS 访问 |

验证 80 端口是否通：

```powershell
Test-NetConnection 8.137.148.233 -Port 80
```

成功标志：

```text
TcpTestSucceeded : True
```

## 5. 登录服务器后先检查基础环境

登录方式：

```text
阿里云 ECS Workbench
MobaXterm
PowerShell ssh
```

你已经可以通过 MobaXterm 登录服务器。

服务器检查命令：

```bash
java -version
mvn -version
nginx -v
mysql --version
libreoffice --version
```

这些命令的作用：

| 命令 | 作用 |
|---|---|
| `java -version` | 检查能不能运行 Spring Boot 后端 |
| `mvn -version` | 检查 Maven，后续服务器源码构建会用到 |
| `nginx -v` | 检查 Nginx 是否安装 |
| `mysql --version` | 检查 MySQL 是否安装 |
| `libreoffice --version` | 检查 Word 转 PDF 工具是否安装 |

本次服务器返回：

```text
openjdk version "21.0.10"
Apache Maven 3.6.3
nginx version: nginx/1.18.0
mysql Ver 8.0.45
LibreOffice 7.3.7.2
```

说明服务器具备运行条件。

## 6. 本地代码是否修改过

严格说，本次部署前做过少量“生产部署必要修改”，不是业务功能重写。

修改目的：

```text
让项目能在 Linux 服务器正常启动。
让默认模板路径不再写死 Windows 路径。
让空 MySQL 数据库能自动创建基础表。
让生产环境配置和本地开发配置分开。
```

对应提交：

```text
c9d27af Prepare production deployment
```

主要文件：

```text
backend/src/main/resources/application-prod.yml
backend/src/main/resources/application.yml
backend/src/main/java/cn/edu/nsu/maic/config/BaseSchemaInitializer.java
backend/src/main/java/cn/edu/nsu/maic/config/CoursePlanSchemaInitializer.java
backend/src/main/java/cn/edu/nsu/maic/config/DataInitializer.java
backend/src/main/java/cn/edu/nsu/maic/controller/CoursePlanController.java
deploy/server-install.sh
```

为什么必须改：

```text
原来默认教案模板路径是 E:\nsu-edu-maic\...，Linux 服务器没有 E 盘。
服务器 MySQL 是空的，如果没有 sys_user 等基础表，登录接口会报错。
生产环境的数据库密码、DeepSeek Key 不能写死在代码里。
```

## 7. GitHub 仓库是什么，做了什么

GitHub 私有仓库：

```text
https://github.com/cgm-free/chengdu-neusoft-ai-lesson-plan-system.git
```

GitHub 的作用：

```text
保存源码。
以后可以多人协作。
以后可以改成服务器从 GitHub 拉代码部署。
```

本次实际部署方式：

```text
不是服务器从 GitHub 拉取部署。
而是本地构建 jar 和 dist，打包 zip，上传到服务器部署。
```

为什么这次不直接服务器拉 GitHub：

```text
GitHub 是私有仓库。
服务器拉私有仓库需要配置 GitHub Token 或 Deploy Key。
为了先把系统稳定跑起来，本次采用本地打包上传，路径更直接。
```

本地查看 GitHub remote：

```powershell
git remote -v
```

推送到 GitHub 的典型命令：

```powershell
git add .
git commit -m "Prepare production deployment"
git push origin main
```

解释：

| 命令 | 作用 |
|---|---|
| `git add .` | 把当前修改加入准备提交区 |
| `git commit -m "..."` | 生成一次本地提交 |
| `git push origin main` | 推送到 GitHub 的 main 分支 |

## 8. 本地构建后端

本机默认 Java 可能不是 21，所以构建前显式指定 JDK 21。

执行目录：

```text
E:\nsu-edu-maic\backend
```

命令：

```powershell
cd E:\nsu-edu-maic\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean package -DskipTests
```

解释：

| 命令 | 作用 |
|---|---|
| `cd ...\backend` | 进入后端项目目录 |
| `$env:JAVA_HOME=...` | 让当前 PowerShell 使用 JDK 21 |
| `$env:Path=...` | 让 `java`、`javac` 命令优先使用 JDK 21 |
| `mvn clean package -DskipTests` | 清理旧编译产物，重新打包 jar，不跑测试 |

成功输出文件：

```text
E:\nsu-edu-maic\backend\target\nsu-edu-maic-backend-0.0.1-SNAPSHOT.jar
```

## 9. 本地构建前端

执行目录：

```text
E:\nsu-edu-maic\frontend
```

命令：

```powershell
cd E:\nsu-edu-maic\frontend
npm run build
```

解释：

| 命令 | 作用 |
|---|---|
| `npm run build` | 把 Vue 项目编译成浏览器可直接访问的静态文件 |

成功输出目录：

```text
E:\nsu-edu-maic\frontend\dist
```

## 10. 本地打包部署包

本次部署包路径：

```text
E:\nsu-edu-maic\tmp\chengdu-neusoft-ai-lesson-plan-system-deploy.zip
```

部署包内容：

```text
backend/app.jar
frontend/dist/
templates/20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx
deploy/server-install.sh
```

各自作用：

| 文件/目录 | 作用 |
|---|---|
| `backend/app.jar` | 后端可执行包 |
| `frontend/dist/` | 前端网页静态文件 |
| `templates/*.docx` | 系统默认课程教案模板 |
| `deploy/server-install.sh` | 服务器安装脚本 |

## 11. SSH 密钥是什么，为什么要配置

SSH 密钥用于让本机安全登录服务器，不用每次输入密码。

本次生成的本地私钥：

```text
C:\Users\Again\.ssh\nsu_maic_deploy
```

生成命令：

```powershell
ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\nsu_maic_deploy -N "" -C "nsu-maic-deploy"
```

解释：

| 参数 | 含义 |
|---|---|
| `-t ed25519` | 使用 ed25519 类型密钥 |
| `-f ...` | 指定密钥文件保存位置 |
| `-N ""` | 不设置本地私钥密码 |
| `-C "nsu-maic-deploy"` | 给密钥加备注 |

服务器端需要把公钥写入：

```text
/root/.ssh/authorized_keys
```

验证 SSH：

```powershell
ssh -i $env:USERPROFILE\.ssh\nsu_maic_deploy root@8.137.148.233 "echo ssh-key-ok && hostname && pwd"
```

成功标志：

```text
ssh-key-ok
iZ2vccu75clgpwffkoxmycZ
/root
```

## 12. 上传部署包到服务器

命令：

```powershell
scp -i $env:USERPROFILE\.ssh\nsu_maic_deploy `
  E:\nsu-edu-maic\tmp\chengdu-neusoft-ai-lesson-plan-system-deploy.zip `
  root@8.137.148.233:/root/chengdu-neusoft-ai-lesson-plan-system-deploy.zip
```

解释：

| 部分 | 作用 |
|---|---|
| `scp` | 通过 SSH 上传文件 |
| `-i ...nsu_maic_deploy` | 使用指定私钥 |
| 本地 zip 路径 | 要上传的部署包 |
| `root@8.137.148.233:/root/...` | 上传到服务器 `/root` 目录 |

注意：

```text
上传 zip 只是把文件放到服务器，还没有安装，也没有启动系统。
```

## 13. 执行服务器安装脚本

服务器上解压：

```bash
rm -rf /root/nsu-maic-deploy
mkdir -p /root/nsu-maic-deploy
unzip -q -o /root/chengdu-neusoft-ai-lesson-plan-system-deploy.zip -d /root/nsu-maic-deploy
```

解释：

| 命令 | 作用 |
|---|---|
| `rm -rf /root/nsu-maic-deploy` | 删除旧临时解压目录 |
| `mkdir -p ...` | 创建新的临时解压目录 |
| `unzip -q -o ...` | 解压部署包 |

处理脚本换行并执行：

```bash
sed -i 's/\r$//' /root/nsu-maic-deploy/deploy/server-install.sh
chmod +x /root/nsu-maic-deploy/deploy/server-install.sh
DB_PASSWORD='你的数据库密码' AI_API_KEY='你的DeepSeekKey' bash /root/nsu-maic-deploy/deploy/server-install.sh
```

解释：

| 命令 | 作用 |
|---|---|
| `sed -i 's/\r$//'` | 去掉 Windows 换行符，避免 Linux 脚本执行异常 |
| `chmod +x` | 给脚本执行权限 |
| `DB_PASSWORD=...` | 传入 MySQL 业务用户密码 |
| `AI_API_KEY=...` | 传入 DeepSeek API Key |
| `bash server-install.sh` | 执行安装 |

注意：

```text
数据库密码和 API Key 只通过环境变量传给脚本。
不要写进 GitHub。
不要写进文档。
```

## 14. 安装脚本具体做了什么

脚本路径：

```text
E:\nsu-edu-maic\deploy\server-install.sh
```

服务器执行后，它做了这些事：

1. 检查命令是否存在：

```bash
java
mysql
nginx
libreoffice
```

2. 创建部署目录：

```text
/opt/chengdu-neusoft-ai-lesson-plan-system
```

3. 复制后端 jar：

```text
/opt/chengdu-neusoft-ai-lesson-plan-system/backend/app.jar
```

4. 复制前端 dist：

```text
/opt/chengdu-neusoft-ai-lesson-plan-system/frontend/dist
```

5. 复制默认教案模板：

```text
/opt/chengdu-neusoft-ai-lesson-plan-system/data/templates/
```

6. 创建 MySQL 数据库：

```sql
CREATE DATABASE IF NOT EXISTS nsu_edu_maic DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

7. 创建 MySQL 用户：

```sql
CREATE USER IF NOT EXISTS 'nsu_maic'@'localhost' IDENTIFIED BY '数据库密码';
GRANT ALL PRIVILEGES ON nsu_edu_maic.* TO 'nsu_maic'@'localhost';
FLUSH PRIVILEGES;
```

8. 写入后端环境变量文件：

```text
/etc/nsu-maic-backend.env
```

9. 创建后端 systemd 服务：

```text
/etc/systemd/system/nsu-maic-backend.service
```

10. 创建 Nginx 配置：

```text
/etc/nginx/sites-available/nsu-maic
/etc/nginx/sites-enabled/nsu-maic
```

11. 启动服务：

```bash
systemctl daemon-reload
systemctl enable nsu-maic-backend
systemctl restart nsu-maic-backend
systemctl restart nginx
```

## 15. 服务器最终目录结构

```text
/opt/chengdu-neusoft-ai-lesson-plan-system/
  backend/
    app.jar
  frontend/
    dist/
      index.html
      assets/
  data/
    templates/
      20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx
    uploads/
      tmp/
  logs/
    backend.out.log
    backend.err.log
```

解释：

| 路径 | 作用 |
|---|---|
| `backend/app.jar` | 当前运行的后端程序 |
| `frontend/dist` | 当前运行的前端页面 |
| `data/templates` | 默认教案模板 |
| `data/uploads/tmp` | 上传过程临时目录 |
| `logs` | 后端日志 |

## 16. 后端环境变量和大模型 API Key 在哪里

大模型 API Key 不在浏览器里，也不在前端代码里。

它在服务器：

```text
/etc/nsu-maic-backend.env
```

查看命令：

```bash
cat /etc/nsu-maic-backend.env
```

文件内容类似：

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/nsu_edu_maic?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=nsu_maic
DB_PASSWORD=这里是真实数据库密码
AI_API_KEY=这里是真实DeepSeekKey
AI_MODEL_NAME=deepseek-v4-flash
AI_BASE_URL=https://api.deepseek.com
CORS_ALLOWED_ORIGIN_PATTERNS=https://nsu-lesson.xyz,https://www.nsu-lesson.xyz,http://8.137.148.233,http://localhost:*,http://127.0.0.1:*
UPLOAD_TMP_DIR=/opt/chengdu-neusoft-ai-lesson-plan-system/data/uploads/tmp
COURSE_PLAN_DEFAULT_TEMPLATE_PATH=/opt/chengdu-neusoft-ai-lesson-plan-system/data/templates/20XX-20XX学年第X学期《课程名称》-课程教案（模版).docx
OCR_ENABLED=false
```

如果要换 DeepSeek Key：

```bash
nano /etc/nsu-maic-backend.env
```

找到这一行：

```env
AI_API_KEY=旧Key
```

改成：

```env
AI_API_KEY=新Key
```

保存后重启后端：

```bash
systemctl restart nsu-maic-backend
```

验证：

```bash
systemctl status nsu-maic-backend --no-pager
curl http://127.0.0.1:8080/api/health
```

重点：

```text
改 API Key 不需要重新部署前端。
改 API Key 不需要重新打包后端。
只需要改 /etc/nsu-maic-backend.env，然后重启后端服务。
```

## 16.1 生产域名登录 403 怎么排查

本次 HTTPS 部署后，浏览器登录曾出现：

```text
POST https://nsu-lesson.xyz/api/auth/login 403 (Forbidden)
```

命令行 `curl` 不带浏览器 `Origin` 请求头时可以登录，但浏览器登录失败。最终定位为后端 CORS 没有允许生产域名。

CORS 可以理解为：

```text
浏览器为了安全，会告诉后端“这个请求来自哪个网页域名”。
如果后端没有允许这个来源，Spring 会拒绝请求。
```

生产环境允许来源配置在：

```text
/etc/nsu-maic-backend.env
```

关键配置：

```env
CORS_ALLOWED_ORIGIN_PATTERNS=https://nsu-lesson.xyz,https://www.nsu-lesson.xyz,http://8.137.148.233,http://localhost:*,http://127.0.0.1:*
```

如果以后换域名，需要把新域名加入这一行，例如：

```env
CORS_ALLOWED_ORIGIN_PATTERNS=https://新域名,https://www.新域名,http://8.137.148.233,http://localhost:*,http://127.0.0.1:*
```

改完后重启后端：

```bash
systemctl restart nsu-maic-backend
```

验证带 `Origin` 的登录请求：

```powershell
'{"username":"admin","password":"admin123456"}' | Set-Content -Encoding ascii $env:TEMP\login-admin.json

curl.exe -i -X POST https://nsu-lesson.xyz/api/auth/login `
  -H "Content-Type: application/json" `
  -H "Origin: https://nsu-lesson.xyz" `
  --data-binary "@$env:TEMP\login-admin.json"
```

成功时应该看到：

```text
HTTP/1.1 200
Access-Control-Allow-Origin: https://nsu-lesson.xyz
```

## 17. 用户上传文件保存在哪里

当前系统上传课程标准、PPT、教学日历、参考资料后，最终主要保存在 MySQL 数据库，不是普通文件夹。

主要表：

```text
course_plan_material
course_plan_generation_job_material
```

含义：

| 表 | 作用 |
|---|---|
| `course_plan_material` | 课程教案生成成功后的长期材料 |
| `course_plan_generation_job_material` | 后台生成任务运行期间使用的材料 |

主要字段：

| 字段 | 含义 |
|---|---|
| `course_plan_id` | 属于哪一份课程教案 |
| `job_id` | 属于哪一个后台生成任务 |
| `role` | 文件角色，例如模板、课程标准、PPT、教学日历 |
| `file_name` | 原始文件名 |
| `file_type` | 文件类型 |
| `file_blob` | 文件二进制内容 |
| `sort_order` | 排序 |
| `created_at` | 上传/保存时间 |

查看已保存文件名称：

```bash
mysql -unsu_maic -p nsu_edu_maic
```

进入 MySQL 后：

```sql
select course_plan_id, role, file_name, file_type, sort_order, created_at
from course_plan_material
order by course_plan_id desc, role, sort_order;
```

查看生成任务材料：

```sql
select job_id, role, file_name, file_type, sort_order, created_at
from course_plan_generation_job_material
order by job_id desc, role, sort_order;
```

查看数据库里材料占用大小：

```sql
select table_name,
       round((data_length + index_length) / 1024 / 1024, 2) as size_mb
from information_schema.tables
where table_schema = 'nsu_edu_maic'
  and table_name in ('course_plan_material', 'course_plan_generation_job_material');
```

临时上传目录：

```text
/opt/chengdu-neusoft-ai-lesson-plan-system/data/uploads/tmp
```

解释：

```text
这个目录主要是上传过程中临时使用。
业务上成功保存的材料主要在 MySQL 的 blob 字段里。
```

## 18. 能不能直接看到用户上传的文件

可以看到文件名、类型、所属教案、上传时间。

命令：

```sql
select course_plan_id, role, file_name, created_at
from course_plan_material
order by created_at desc;
```

但不能像 Windows 文件夹一样直接双击打开，因为文件本体在 MySQL 的 `file_blob` 二进制字段里。

如果后续要方便管理员查看或下载上传原件，建议增加一个后台功能：

```text
教案详情 -> 上传材料列表 -> 下载原始文件
```

正式生产更推荐改成：

```text
文件本体：阿里云 OSS
数据库：只保存文件名、OSS 路径、大小、类型
```

这样更容易查看、备份、清理。

## 19. 上传文件会不会自动删除

目前没有自动删除策略。

当前行为：

```text
生成成功后的课程材料会保存在数据库。
生成任务材料也会保存在数据库。
临时上传目录可能留下一些临时文件。
```

风险：

```text
如果很多老师长期上传 PPT、PDF、DOCX，MySQL 会越来越大。
40 GiB 系统盘可能被数据库和日志占满。
```

建议后续增加：

1. 生成任务临时材料保留 7 天后自动删除。
2. 删除课程教案时，同步删除对应 `course_plan_material`。
3. 定期清理 `/opt/.../data/uploads/tmp`。
4. 长期文件迁移到阿里云 OSS。
5. 每天备份 MySQL。

查看磁盘：

```bash
df -h
du -sh /opt/chengdu-neusoft-ai-lesson-plan-system/*
```

查看 MySQL 数据库大小：

```sql
select table_schema as db,
       round(sum(data_length + index_length) / 1024 / 1024, 2) as size_mb
from information_schema.tables
where table_schema = 'nsu_edu_maic'
group by table_schema;
```

## 20. Nginx 是什么，当前怎么配置

Nginx 负责两件事：

```text
1. 把前端 dist 文件作为网页发给浏览器。
2. 把 /api 请求转发给本机 8080 端口的后端。
```

配置文件：

```text
/etc/nginx/sites-available/nsu-maic
```

当前核心配置：

```nginx
server {
    listen 80;
    server_name nsu-lesson.xyz www.nsu-lesson.xyz;

    root /opt/chengdu-neusoft-ai-lesson-plan-system/frontend/dist;

    location ^~ /.well-known/acme-challenge/ {
        default_type text/plain;
        root /var/www/html;
        try_files $uri =404;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    server_name nsu-lesson.xyz www.nsu-lesson.xyz;

    client_max_body_size 300m;

    root /opt/chengdu-neusoft-ai-lesson-plan-system/frontend/dist;
    index index.html;

    ssl_certificate /etc/letsencrypt/live/nsu-lesson.xyz/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/nsu-lesson.xyz/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 1800s;
        proxy_send_timeout 1800s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

重要解释：

| 配置 | 含义 |
|---|---|
| `listen 80` | 接收 HTTP 请求 |
| `return 301 https://...` | 自动跳转 HTTPS |
| `listen 443 ssl` | 接收 HTTPS 请求 |
| `client_max_body_size 300m` | 允许上传较大文件 |
| `location /api/` | API 转发到后端 |
| `try_files ... /index.html` | 支持 Vue 路由，例如 `/login` |
| `.well-known/acme-challenge` | 给证书自动续期验证使用 |

修改 Nginx 后必须执行：

```bash
nginx -t
systemctl reload nginx
```

解释：

| 命令 | 作用 |
|---|---|
| `nginx -t` | 检查配置有没有语法错误 |
| `systemctl reload nginx` | 平滑重载 Nginx |

## 21. systemd 后端服务

后端服务名：

```text
nsu-maic-backend
```

服务文件：

```text
/etc/systemd/system/nsu-maic-backend.service
```

常用命令：

```bash
systemctl status nsu-maic-backend --no-pager
systemctl restart nsu-maic-backend
systemctl stop nsu-maic-backend
systemctl start nsu-maic-backend
```

解释：

| 命令 | 作用 |
|---|---|
| `status` | 看后端是否运行 |
| `restart` | 重启后端，改环境变量后必须做 |
| `stop` | 停止后端 |
| `start` | 启动后端 |

查看日志：

```bash
tail -n 200 /opt/chengdu-neusoft-ai-lesson-plan-system/logs/backend.out.log
tail -n 200 /opt/chengdu-neusoft-ai-lesson-plan-system/logs/backend.err.log
journalctl -u nsu-maic-backend -n 200 --no-pager
```

## 22. 域名注册和 DNS 解析

新域名：

```text
nsu-lesson.xyz
```

注册时间：

```text
2026-04-25 17:05:37
```

DNS 服务器：

```text
dns9.hichina.com
dns10.hichina.com
```

解析记录：

| 主机记录 | 记录类型 | 记录值 | 含义 |
|---|---|---|---|
| `@` | A | `8.137.148.233` | 根域名 `nsu-lesson.xyz` 指向服务器 |
| `www` | A | `8.137.148.233` | `www.nsu-lesson.xyz` 指向服务器 |

什么是 A 记录：

```text
A 记录就是把域名指向一个 IPv4 地址。
浏览器访问 nsu-lesson.xyz 时，DNS 会告诉浏览器它对应 8.137.148.233。
```

查询权威 DNS：

```powershell
nslookup nsu-lesson.xyz dns9.hichina.com
nslookup www.nsu-lesson.xyz dns9.hichina.com
```

查询公共 DNS：

```powershell
nslookup nsu-lesson.xyz 223.5.5.5
nslookup www.nsu-lesson.xyz 223.5.5.5
```

当前结果已经正确：

```text
nsu-lesson.xyz       -> 8.137.148.233
www.nsu-lesson.xyz   -> 8.137.148.233
```

## 23. HTTPS 证书配置

HTTPS 使用 Let's Encrypt 免费证书。

安装 Certbot：

```bash
apt-get update -y
apt-get install -y certbot python3-certbot-nginx
```

申请证书：

```bash
certbot --nginx -d nsu-lesson.xyz -d www.nsu-lesson.xyz --non-interactive --agree-tos --email ChuGuoming@nsu.edu.cn --redirect
```

解释：

| 参数 | 含义 |
|---|---|
| `--nginx` | 让 Certbot 自动修改 Nginx 配置 |
| `-d nsu-lesson.xyz` | 给根域名申请证书 |
| `-d www.nsu-lesson.xyz` | 给 www 域名申请证书 |
| `--agree-tos` | 同意 Let's Encrypt 条款 |
| `--email ...` | 证书通知邮箱 |
| `--redirect` | 自动把 HTTP 跳转到 HTTPS |

本次证书结果：

```text
Certificate is saved at: /etc/letsencrypt/live/nsu-lesson.xyz/fullchain.pem
Key is saved at:         /etc/letsencrypt/live/nsu-lesson.xyz/privkey.pem
This certificate expires on 2026-07-24.
```

证书自动续期测试：

```bash
certbot renew --dry-run
```

当前结果：

```text
Congratulations, all simulated renewals succeeded
```

注意：

```text
一开始 dry-run 失败过，因为 Nginx 的 HTTP server 被 Certbot 改成直接 return 404/redirect。
后来手动保留了 /.well-known/acme-challenge/ 路径，续期测试已通过。
```

## 24. 部署完成后的验证命令

服务器内部验证后端：

```bash
curl http://127.0.0.1:8080/api/health
```

服务器内部通过 Nginx 验证：

```bash
curl http://127.0.0.1/api/health
```

公网 HTTP 验证：

```powershell
curl.exe -I http://nsu-lesson.xyz/login
```

成功时会返回：

```text
HTTP/1.1 301 Moved Permanently
Location: https://nsu-lesson.xyz/login
```

公网 HTTPS 验证：

```powershell
curl.exe -I https://nsu-lesson.xyz/login
```

成功时会返回：

```text
HTTP/1.1 200 OK
```

API 验证：

```powershell
curl.exe https://nsu-lesson.xyz/api/health
```

成功时会返回：

```json
{"success":true,"message":"success","data":{"name":"成都东软学院智能教案生成系统","status":"ok"}}
```

## 25. 登录接口验证

默认账号：

```text
admin / admin123456
```

登录测试：

```powershell
$login = Invoke-RestMethod -Uri 'https://nsu-lesson.xyz/api/auth/login' `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"admin123456"}'

$token = $login.data.token

Invoke-RestMethod -Uri 'https://nsu-lesson.xyz/api/auth/me' `
  -Headers @{ 'X-Auth-Token' = $token }
```

解释：

| 命令 | 作用 |
|---|---|
| `/api/auth/login` | 登录并拿 token |
| `/api/auth/me` | 用 token 查询当前用户 |

## 26. 以后更新系统怎么部署

当前推荐继续使用“本地构建 + 上传 zip”的方式。

### 26.1 本地构建

后端：

```powershell
cd E:\nsu-edu-maic\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean package -DskipTests
```

前端：

```powershell
cd E:\nsu-edu-maic\frontend
npm run build
```

### 26.2 上传并重新安装

上传：

```powershell
scp -i $env:USERPROFILE\.ssh\nsu_maic_deploy `
  E:\nsu-edu-maic\tmp\chengdu-neusoft-ai-lesson-plan-system-deploy.zip `
  root@8.137.148.233:/root/chengdu-neusoft-ai-lesson-plan-system-deploy.zip
```

服务器执行：

```bash
rm -rf /root/nsu-maic-deploy
mkdir -p /root/nsu-maic-deploy
unzip -q -o /root/chengdu-neusoft-ai-lesson-plan-system-deploy.zip -d /root/nsu-maic-deploy
sed -i 's/\r$//' /root/nsu-maic-deploy/deploy/server-install.sh
chmod +x /root/nsu-maic-deploy/deploy/server-install.sh
DB_PASSWORD='保持原来的数据库密码' AI_API_KEY='保持或更换DeepSeekKey' bash /root/nsu-maic-deploy/deploy/server-install.sh
```

注意：

```text
重复执行安装脚本会替换 app.jar 和 frontend/dist。
数据库会保留已有数据。
如果 DB_PASSWORD 和原来不一致，可能导致后端连不上 MySQL。
```

## 27. 以后如果要改成服务器从 GitHub 拉取

本次没有用这个方式，但以后可以。

需要先配置服务器访问 GitHub 私有仓库：

```text
方式一：GitHub Personal Access Token
方式二：GitHub Deploy Key
```

服务器拉取源码后构建的大致流程：

```bash
cd /opt
git clone https://github.com/cgm-free/chengdu-neusoft-ai-lesson-plan-system.git
cd chengdu-neusoft-ai-lesson-plan-system/backend
mvn clean package -DskipTests
cd ../frontend
npm install
npm run build
```

现在不建议立刻切换，因为：

```text
服务器上 npm 依赖安装、GitHub 私库认证、构建缓存都还没有整理。
当前本地打包上传更容易排错。
```

## 28. 常见维护命令

查看服务状态：

```bash
systemctl status nsu-maic-backend --no-pager
systemctl status nginx --no-pager
```

重启服务：

```bash
systemctl restart nsu-maic-backend
systemctl restart nginx
```

查看后端日志：

```bash
tail -n 200 /opt/chengdu-neusoft-ai-lesson-plan-system/logs/backend.out.log
tail -n 200 /opt/chengdu-neusoft-ai-lesson-plan-system/logs/backend.err.log
```

查看 Nginx 日志：

```bash
tail -n 100 /var/log/nginx/access.log
tail -n 100 /var/log/nginx/error.log
```

查看端口监听：

```bash
ss -lntp | grep -E ':80|:443|:8080|:3306'
```

解释：

| 端口 | 应该由谁监听 |
|---|---|
| 80 | Nginx |
| 443 | Nginx |
| 8080 | Java 后端 |
| 3306 | MySQL |

查看磁盘：

```bash
df -h
du -sh /opt/chengdu-neusoft-ai-lesson-plan-system/*
```

## 29. 备份

备份 MySQL：

```bash
mysqldump -unsu_maic -p nsu_edu_maic > /root/nsu_edu_maic_backup_$(date +%F).sql
```

备份应用目录：

```bash
tar -czf /root/nsu-maic-app-backup_$(date +%F).tar.gz /opt/chengdu-neusoft-ai-lesson-plan-system
```

下载备份到本地：

```powershell
scp -i $env:USERPROFILE\.ssh\nsu_maic_deploy `
  root@8.137.148.233:/root/nsu_edu_maic_backup_2026-04-25.sql `
  E:\backup\
```

正式使用前建议：

```text
至少每天备份数据库。
至少每次发版前备份应用目录。
```

## 30. 重要风险和后续改进

当前已经能公网访问，但还不是完整生产级。

建议后续补齐：

1. 修改默认管理员密码。
2. 增加上传材料自动清理策略。
3. 把上传文件从 MySQL blob 迁移到阿里云 OSS。
4. 配置 MySQL 定时备份。
5. 配置后端日志切割，避免日志撑满磁盘。
6. 增加服务器监控告警。
7. 如果学校正式使用，需要完成 ICP 备案。

## 31. 当前部署结论

当前状态：

```text
ECS 已部署成功。
Nginx 已运行。
Spring Boot 后端已运行。
MySQL 已运行。
LibreOffice 已可用。
域名 nsu-lesson.xyz 已解析到 8.137.148.233。
HTTPS 证书已申请成功。
Certbot 自动续期 dry-run 已通过。
```

当前可访问地址：

```text
https://nsu-lesson.xyz/login
https://www.nsu-lesson.xyz/login
```

当前关键维护文件：

```text
/etc/nsu-maic-backend.env
/etc/nginx/sites-available/nsu-maic
/etc/systemd/system/nsu-maic-backend.service
/opt/chengdu-neusoft-ai-lesson-plan-system/
```

最常用的三条维护命令：

```bash
systemctl status nsu-maic-backend --no-pager
tail -n 200 /opt/chengdu-neusoft-ai-lesson-plan-system/logs/backend.err.log
systemctl restart nsu-maic-backend
```
