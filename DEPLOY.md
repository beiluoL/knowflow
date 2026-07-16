# 部署指南 (Deployment)

本文说明 `learnbase` 的生产环境部署方式：后端打 jar 运行（可切换 MySQL），前端构建后由 Nginx 托管，并给出 Docker 一键部署示例。

> 开发态启动方式见根目录 [README.md](./README.md)。本文聚焦生产部署。

## 1. 环境要求

- **后端**：JDK 17、Maven 3.8+（或用 `backend/.mvn` 内置包装器）
- **前端**：Node.js 20+、npm
- **可选**：MySQL 8、Docker / Docker Compose

## 2. 后端部署

### 2.1 打包

```bash
cd backend
./mvnw clean package -DskipTests        # 生成 target/zhishiku-backend-1.0.0.jar
# 或：mvn clean package -DskipTests
```

### 2.2 运行（默认 H2 内存库）

```bash
java -jar target/zhishiku-backend-1.0.0.jar
```

默认监听 `8080`。低权限/无外部数据库时可直接跑起来（数据在内存，重启即清空）。

### 2.3 生产配置（切换 MySQL）

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://<host>:3306/zhishiku?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: prod_user
    password: ******      # 改为强密码
  sql:
    init:
      mode: always        # 首次部署建表+写入种子数据；之后改 never
jwt:
  secret: ******          # 改为强随机值（默认值是开发占位符）
```

也可用命令行参数覆盖，避免把密码写进配置文件：

```bash
java -jar target/zhishiku-backend-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://<host>:3306/zhishiku \
  --spring.datasource.username=prod_user \
  --spring.datasource.password='******' \
  --jwt.secret='******'
```

### 2.4 进程守护（systemd 示例）

`/etc/systemd/system/learnbase-backend.service`：

```ini
[Unit]
Description=learnbase backend
After=network.target

[Service]
User=app
WorkingDirectory=/opt/learnbase/backend
ExecStart=/usr/bin/java -jar /opt/learnbase/backend/target/zhishiku-backend-1.0.0.jar
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now learnbase-backend
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

将 `frontend/dist/` 放到服务器（如 `/opt/learnbase/frontend/dist`），并用下面的配置托管。

### 3.3 Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/learnbase/frontend/dist;
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
COPY --from=build /app/target/zhishiku-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`docker-compose.yml`（MySQL + 后端；前端仍用 Nginx 托管 `dist/`）：

```yaml
services:
  db:
    image: mysql:8
    environment:
      MYSQL_DATABASE: zhishiku
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
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/zhishiku?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: change_me
      JWT_SECRET: change_me_to_a_long_random_secret
      SPRING_SQL_INIT_MODE: always
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
| `admin` | `123456` | 管理员 |
| `user1` | `123456` | 普通用户 |
| `user2` | `123456` | 普通用户 |

生产环境请修改密码、更换 `jwt.secret`，并将 `spring.sql.init.mode` 改为 `never`。
