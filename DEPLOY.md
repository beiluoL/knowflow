# 部署指南 (Deployment)

本文说明 `knowflow` 的生产环境部署方式：后端打 jar 运行（可切换 MySQL），前端构建后由 Nginx 托管，并给出 Docker 一键部署示例。

> 开发态启动方式见根目录 [README.md](./README.md)。本文聚焦生产部署。

## 1. 环境要求

- **后端**：JDK 17、Maven 3.8+（或用 `backend/.mvn` 内置包装器）
- **前端**：Node.js 20+、npm
- **可选**：MySQL 8、Docker / Docker Compose

## 2. 后端部署

### 2.1 打包

```bash
cd backend
./mvnw clean package -DskipTests        # 生成 target/knowflow-backend-1.0.0.jar
# 或：mvn clean package -DskipTests
```

### 2.2 运行（默认 H2 内存库）

```bash
java -jar target/knowflow-backend-1.0.0.jar
```

默认监听 `8080`。低权限/无外部数据库时可直接跑起来（数据在内存，重启即清空）。

### 2.3 生产配置（切换 MySQL）

先在 MySQL 中创建数据库（**务必使用 utf8mb4**，否则中文/emoji 写入异常）：

```sql
CREATE DATABASE knowflow DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

再编辑 `backend/src/main/resources/application.yml`，**只需把 `type` 改为 `mysql`**，
系统启动时会自动加载 MySQL 驱动与 `db/mysql/` 方言脚本，无需改动任何代码：

```yaml
knowflow:
  datasource:
    type: mysql                 # h2（开发测试）/ mysql（生产）
    allow-runtime-switch: false # 生产建议关闭后台热切换，防误操作
    mysql:
      url: jdbc:mysql://<host>:3306/knowflow?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=true
      username: prod_user
      password: ******          # 改为强密码
      init-mode: auto           # auto：库为空时自动建表灌数据；已有数据则跳过
      maximum-pool-size: 20
jwt:
  secret: ******                # 改为强随机值（默认值是开发占位符）
```

推荐用环境变量注入，避免把密码写进配置文件：

```bash
DB_TYPE=mysql \
MYSQL_URL='jdbc:mysql://<host>:3306/knowflow?characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai' \
MYSQL_USERNAME=prod_user \
MYSQL_PASSWORD='******' \
MYSQL_INIT_MODE=auto \
DB_ALLOW_RUNTIME_SWITCH=false \
java -jar target/knowflow-backend-1.0.0.jar --jwt.secret='******'
```

**`init-mode` 取值说明**：

| 取值 | 行为 | 建议 |
|------|------|------|
| `auto` | 库中无业务表时才建表并灌演示数据 | 首次部署推荐 |
| `never` | 从不执行脚本，结构由 DBA / 迁移工具管理 | 表结构稳定后推荐 |
| `always` | 每次启动都执行 | **生产禁用**，会重复写入数据 |

> 后台「系统设置 → 数据库设置」页可查看当前库状态、测试连通性并热切换。
> 生产环境建议设 `allow-runtime-switch: false` 关闭该能力，仅保留状态查看。
> 双库语法差异与迁移说明详见 [DATABASE.md 第五章](./DATABASE.md#五双数据库支持h2--mysql-切换)。

**存量 MySQL 库补建全文索引**：`db/mysql/schema.sql` 已内置文档全文索引，但 `init-mode=auto`
只在空库时执行建表。若你的库是本次升级前创建的，需手动补建一次（H2 无需此步）：

```sql
CREATE FULLTEXT INDEX ft_doc_content ON doc_document (title, summary, content) WITH PARSER ngram;
```

该索引用于提升关键词检索性能。未补建时功能不受影响，仅在文档量较大时检索变慢。

### 2.4 进程守护（systemd 示例）

`/etc/systemd/system/knowflow-backend.service`：

```ini
[Unit]
Description=knowflow backend
After=network.target

[Service]
User=app
WorkingDirectory=/opt/knowflow/backend
ExecStart=/usr/bin/java -jar /opt/knowflow/backend/target/knowflow-backend-1.0.0.jar
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now knowflow-backend
```

## 3. 前端部署

### 3.1 构建

```bash
cd frontend
npm install
npm run build            # 产物输出到 frontend/dist/
```

> 前端所有接口请求 `baseURL` 为 `/api`，与后端 Controller 统一的 `/api` 前缀一致，部署时由 Nginx 反代到后端即可，无需路径重写。

### 3.2 用 Nginx 托管静态文件

将 `frontend/dist/` 放到服务器（如 `/opt/knowflow/frontend/dist`），并用下面的配置托管。

### 3.3 Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/knowflow/frontend/dist;
    index index.html;

    # SPA 路由：直接访问 /login 等子路由刷新时不报 404
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反代
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 上传文件反代（图片/附件等静态资源，由后端 Spring 静态资源映射提供）
    # 缺少此配置会导致上传成功后图片无法显示（请求回退到 SPA index.html）
    location /uploads/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 4. Docker 一键部署（可选）

后端 `Dockerfile`（多阶段构建）：

```dockerfile
# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
RUN mvn -q clean package -DskipTests

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/knowflow-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`docker-compose.yml`（MySQL + 后端；前端仍用 Nginx 托管 `dist/`）：

```yaml
services:
  db:
    image: mysql:8
    # 强制 utf8mb4，保证中文与 emoji 正确存储
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    environment:
      MYSQL_DATABASE: knowflow
      MYSQL_ROOT_PASSWORD: change_me
    volumes:
      - db_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    depends_on:
      db:
        condition: service_healthy
    environment:
      # 只需指定 DB_TYPE=mysql，系统自动加载 MySQL 驱动与 db/mysql 方言脚本
      DB_TYPE: mysql
      MYSQL_URL: jdbc:mysql://db:3306/knowflow?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: change_me
      MYSQL_INIT_MODE: auto            # 库为空时自动建表灌数据，重启不清空
      DB_ALLOW_RUNTIME_SWITCH: "false" # 容器环境禁用后台热切换
      JWT_SECRET: change_me_to_a_long_random_secret
    ports:
      - "8080:8080"

volumes:
  db_data:
```

启动：

```bash
docker compose up -d --build
```

## 5. 访问地址

| 服务 | 地址 |
|---|---|
| 前端页面 | `http://<server>`（Nginx） |
| 后端 API | `http://<server>:8080/api/...`（或经 Nginx 的 `/api`） |
| Swagger 文档 | `http://<server>:8080/swagger-ui.html` |
| H2 控制台（仅默认 H2） | `http://<server>:8080/h2-console` |

## 6. 默认账号（上线前务必修改）

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 管理员 |
| `user1` | `admin123` | 普通用户 |
| `user2` | `admin123` | 普通用户 |

> 密码统一为 `admin123`（BCrypt 加密），与 `backend/src/main/resources/data.sql` 中保持一致。

生产环境请修改密码、更换 `jwt.secret`，并将 `spring.sql.init.mode` 改为 `never`。
