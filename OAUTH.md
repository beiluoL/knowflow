# KnowFlow 第三方登录接入指南（GitHub + 微信）

本文档说明 KnowFlow 项目中 GitHub 与微信 OAuth 登录的完整接入流程，包括代码结构、配置方式、密钥获取步骤与本地/线上调试方法。

---

## 一、整体流程

KnowFlow 采用「后端中转」的 OAuth 2.0 授权码模式（Authorization Code Flow），避免把 client_secret 暴露到前端。

```
┌────────┐    1.点击登录       ┌────────┐    2.302 重定向    ┌──────────┐
│ 前端   │ ──────────────────▶ │ 后端   │ ────────────────▶ │ GitHub   │
│ Login  │                     │ /api/  │                   │ /微信    │
│ .vue   │ ◀────────────────── │ auth/  │ ◀──────────────── │ 授权页   │
└────────┘   6.带 token 跳转   │ oauth  │   3.用户授权      └──────────┘
    │        /oauth/callback   └────────┘   4.带 code 回调          │
    │                             │        /api/auth/oauth/         │
    │                             │        github/callback          │
    │                             │                                 │
    │                             └─── 5.换 token+拉用户+签发 JWT ──┘
    │
    ▼
 7.写入 token，调 /auth/me 拉用户信息，跳首页
```

**关键点**：
- 前端只负责触发跳转和接收 token，**不接触 client_secret**
- 后端负责：拼装授权 URL → 接收 code 回调 → 换 access_token → 拉用户信息 → 自动注册/登录 → 签发 JWT
- JWT 通过 URL query 传递回前端（`/oauth/callback?token=xxx`），前端写入 localStorage 后跳首页

---

## 二、代码结构

### 后端（Spring Boot）

| 文件 | 作用 |
|---|---|
| [OAuthConfig.java](backend/src/main/java/com/knowflow/config/OAuthConfig.java) | OAuth 配置类，读取 `oauth.*` 配置，注册 RestTemplate Bean |
| [SecurityConfig.java](backend/src/main/java/com/knowflow/config/SecurityConfig.java) | 放行 `/api/auth/oauth/**` 路径，允许未登录访问 |
| [AuthController.java](backend/src/main/java/com/knowflow/controller/AuthController.java) | OAuth 接口：`/oauth/github`、`/oauth/github/callback`、`/oauth/wechat`、`/oauth/wechat/callback` |
| [UserService.java](backend/src/main/java/com/knowflow/service/UserService.java) | `oauthLogin` 方法签名 |
| [UserServiceImpl.java](backend/src/main/java/com/knowflow/service/impl/UserServiceImpl.java) | `oauthLogin` 实现：按 provider+providerUid 查用户，不存在则自动注册 |
| [SysUser.java](backend/src/main/java/com/knowflow/entity/SysUser.java) | 新增 `provider`、`providerUid` 字段 |
| [schema.sql](backend/src/main/resources/schema.sql) | `sys_user` 表新增 `provider`、`provider_uid` 列，`password` 允许空 |
| [application.yml](backend/src/main/resources/application.yml) | OAuth 凭证配置（占位符） |

### 前端（Vue 3）

| 文件 | 作用 |
|---|---|
| [Login.vue](frontend/src/views/Login.vue) | 「GitHub 登录」「微信登录」按钮，点击跳转到 `/api/auth/oauth/{provider}` |
| [OAuthCallback.vue](frontend/src/views/OAuthCallback.vue) | OAuth 回调中转页：读取 `?token=` 或 `?error=`，写入会话后跳首页 |
| [router/index.ts](frontend/src/router/index.ts) | 新增 `/oauth/callback` 路由 |

---

## 三、接口说明

### 3.1 GitHub 登录入口
```
GET /api/auth/oauth/github
```
- 无参数
- 返回 302，重定向到 `https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=user:email`

### 3.2 GitHub 回调
```
GET /api/auth/oauth/github/callback?code={code}
```
- GitHub 授权后自动回调
- 后端流程：code → access_token → 用户信息 → `oauthLogin("github", ...)` → 302 到 `http://localhost:5173/oauth/callback?token={jwt}`

### 3.3 微信登录入口
```
GET /api/auth/oauth/wechat
```
- 无参数
- 返回 302，重定向到 `https://open.weixin.qq.com/connect/qrconnect?appid=...&redirect_uri=...&response_type=code&scope=snsapi_login&state=knowflow`

### 3.4 微信回调
```
GET /api/auth/oauth/wechat/callback?code={code}&state={state}
```
- 微信授权后自动回调
- 后端流程：code → access_token + openid → 用户信息 → `oauthLogin("wechat", openid, ...)` → 302 到 `http://localhost:5173/oauth/callback?token={jwt}`

---

## 四、配置说明

### 4.1 后端配置（application.yml）

```yaml
oauth:
  frontend-base-url: ${OAUTH_FRONTEND_BASE_URL:http://localhost:5173}
  github:
    client-id: ${GITHUB_CLIENT_ID:your-github-client-id}
    client-secret: ${GITHUB_CLIENT_SECRET:your-github-client-secret}
    redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8080/api/auth/oauth/github/callback}
  wechat:
    app-id: ${WECHAT_APP_ID:your-wechat-app-id}
    app-secret: ${WECHAT_APP_SECRET:your-wechat-app-secret}
    redirect-uri: ${WECHAT_REDIRECT_URI:http://localhost:8080/api/auth/oauth/wechat/callback}
```

**三种配置方式（任选其一）**：

#### 方式 A：直接改 application.yml（不推荐提交到 git）
```yaml
oauth:
  github:
    client-id: Ov23lixxxxxxxxxxxxx
    client-secret: 5c4d2dxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

#### 方式 B：环境变量（推荐，安全）
```bash
export GITHUB_CLIENT_ID=Ov23lixxxxxxxxxxxxx
export GITHUB_CLIENT_SECRET=5c4d2dxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export WECHAT_APP_ID=wx1234567890abcdef
export WECHAT_APP_SECRET=abcdef0123456789abcdef0123456789
mvn spring-boot:run
```

#### 方式 C：IDE 启动配置
在 IntelliJ IDEA 的 Run Configuration → Environment Variables 中填入上述变量。

### 4.2 数据库变更

`sys_user` 表已新增两列，无需手动迁移，H2 启动时自动执行 schema.sql：

```sql
provider     VARCHAR(20),   -- 第三方登录提供方：github / wechat / NULL（账号密码注册）
provider_uid VARCHAR(100)   -- 第三方返回的用户唯一 ID（GitHub 的 id、微信的 openid）
```

密码字段已改为 `DEFAULT ''`，社交登录用户密码留空，不可用于账号密码登录。

---

## 五、获取 GitHub OAuth 凭证

### 步骤

1. 访问 https://github.com/settings/developers
2. 点击「New OAuth App」
3. 填写：
   - **Application name**: KnowFlow（或自定义）
   - **Homepage URL**: `http://localhost:5173`（开发环境）/ 你的线上域名
   - **Application description**: 可选
   - **Authorization callback URL**: `http://localhost:8080/api/auth/oauth/github/callback`
4. 点击「Register application」
5. 在应用详情页：
   - 复制 **Client ID** → 填入 `GITHUB_CLIENT_ID`
   - 点击「Generate a new client secret」→ 复制 **Client Secret** → 填入 `GITHUB_CLIENT_SECRET`
6. （可选）上传应用 logo

**注意事项**：
- Client Secret 只在生成时显示一次，请立即保存
- 回调 URL 必须与 `application.yml` 中的 `redirect-uri` 完全一致（含协议、端口、路径）
- GitHub OAuth App 创建后立即可用，无需审核

---

## 六、获取微信 OAuth 凭证

### 前置条件

微信开放平台的「网站应用」需要**企业/个体工商户资质**才能创建。个人开发者无法直接接入微信扫码登录。

如需个人开发测试，可参考「七、本地无凭证测试方案」。

### 步骤

1. 访问 https://open.weixin.qq.com
2. 完成开发者资质认证（需上传营业执照，审核约 1-3 个工作日）
3. 进入「管理中心 → 网站应用 → 创建网站应用」
4. 填写：
   - **应用名称**: KnowFlow
   - **应用官网**: 你的线上域名（开发期可填 `http://localhost:5173`，但正式审核需真实域名）
   - **授权回调域**: `localhost:8080`（开发期）/ 你的域名（线上）
   
   > ⚠️ 微信只填**域名**，不填完整路径。后端回调路径固定为 `/api/auth/oauth/wechat/callback`
5. 提交审核（约 1-7 个工作日）
6. 审核通过后，在应用详情页：
   - 复制 **AppID** → 填入 `WECHAT_APP_ID`
   - 复制 **AppSecret** → 填入 `WECHAT_APP_SECRET`

**注意事项**：
- 微信回调域只校验 host，不校验路径，所以开发期用 `localhost:8080` 即可
- 微信 access_token 接口返回的 `openid` 是用户在该应用下的唯一标识，不同应用间不互通
- 微信用户信息接口 `sns/userinfo` 需要 scope 为 `snsapi_login`（已在代码中设置）

---

## 七、本地无凭证测试方案

如果你暂时没有 GitHub/微信的正式凭证，可以按以下方式验证代码流程：

### 7.1 GitHub：可直接申请

GitHub OAuth App **无需审核**，注册即用。建议先跑通 GitHub 登录再处理微信。

### 7.2 微信：用占位凭证走流程

保持 `application.yml` 中的占位值 `your-wechat-app-id`，启动后端：

1. 访问 `http://localhost:5173/login`
2. 点击「微信登录」按钮
3. 浏览器会跳转到微信授权页，但因 appid 无效，微信会显示「appid参数错误」
4. 这说明**后端跳转逻辑正确**，只是凭证未配置

### 7.3 模拟回调（绕过 GitHub/微信）

若想测试「回调 → 自动注册 → 签发 JWT → 前端跳转」的完整链路，可手动构造回调请求：

```bash
# 模拟 GitHub 回调（code 随便填，会因为换 token 失败而走 error 分支）
curl -v "http://localhost:8080/api/auth/oauth/github/callback?code=test123"
# 预期：302 跳转到 http://localhost:5173/oauth/callback?error=GitHub%20授权失败...
```

前端 `/oauth/callback` 页面会显示「登录失败」并提示错误信息。

### 7.4 直接调用 oauthLogin（单元测试/调试）

如需跳过 OAuth 提供方，直接测试用户创建逻辑，可在后端写一个临时接口或单元测试调用：

```java
userService.oauthLogin("github", "12345", "测试用户", "https://example.com/avatar.png", "test@example.com");
```

---

## 八、自动注册与登录逻辑

`UserServiceImpl.oauthLogin(provider, providerUid, nickname, avatar, email)` 的行为：

1. **查询用户**：按 `provider + providerUid` 联合查找
2. **不存在 → 自动注册**：
   - 用户名格式：`{provider}_{providerUid}`（如 `github_12345`）
   - 密码留空（社交登录用户不可用账号密码登录）
   - 角色 `USER`，初始等级/经验/精力值与注册接口一致
3. **已存在 → 更新资料**：
   - 若头像或昵称变更，更新到本地（保持与社交平台同步）
4. **签发 JWT**：与账号密码登录使用同一个 `jwtUtils.generateToken`，前端无感知差异

---

## 九、线上部署注意事项

1. **修改回调 URL**：
   - GitHub OAuth App 的 Authorization callback URL 改为 `https://yourdomain.com/api/auth/oauth/github/callback`
   - 微信开放平台的授权回调域改为 `yourdomain.com`
   - `application.yml` 中 `oauth.*.redirect-uri` 和 `oauth.frontend-base-url` 改为线上地址

2. **HTTPS**：
   - 微信要求回调域必须 HTTPS（开发期 localhost 豁免）
   - GitHub 不强制，但生产环境建议 HTTPS

3. **环境变量**：
   - 生产环境**严禁**把 client_secret 写进 application.yml 提交到 git
   - 使用环境变量或配置中心注入

4. **state 参数**：
   - 当前 GitHub 未强制校验 state（已接收但未验证）
   - 微信用了固定值 `knowflow`
   - 生产环境建议改为随机值并存 session/redis 校验，防 CSRF

5. **账号绑定**：
   - 当前实现：同一社交账号首次登录自动注册新用户
   - 可选增强：若已登录用户点击社交绑定，将 provider+providerUid 关联到现有用户（需新增绑定接口）

---

## 十、故障排查

| 现象 | 原因 | 解决方案 |
|---|---|---|
| 点击登录后 404 | 后端未启动或路由未放行 | 确认 8080 端口，检查 SecurityConfig 是否放行 `/api/auth/oauth/**` |
| GitHub 显示「Invalid redirect_uri」 | OAuth App 的回调 URL 与配置不一致 | 修改 GitHub OAuth App 的 callback URL 或 application.yml 的 redirect-uri |
| 微信显示「appid参数错误」 | 未配置真实 AppID | 按第六节获取凭证 |
| 回调后前端显示「授权失败：未获取到 access_token」 | code 失效或凭证错误 | 查看后端日志 `log.warn("...OAuth 换 token 失败...")` |
| 回调后前端显示「获取用户信息失败」 | token 已写入但 /auth/me 401 | 检查 JWT 密钥是否变更，或 token 是否过期 |
| 用户名冲突 | 同一 provider+providerUid 已注册过 | 正常现象，会走「已存在」分支登录 |

---

## 十一、验证清单

完成凭证配置后，按以下步骤验证：

- [ ] 后端启动无报错，日志显示 `Started KnowFlowApplication`
- [ ] 访问 `http://localhost:8080/api/auth/oauth/github` 返回 302 到 github.com
- [ ] 访问 `http://localhost:8080/api/auth/oauth/wechat` 返回 302 到 open.weixin.qq.com
- [ ] 前端 `/login` 页面点击 GitHub 按钮，跳转到 GitHub 授权页
- [ ] GitHub 授权后回调到 `/oauth/callback`，显示「登录成功」并跳转首页
- [ ] 首页右上角显示 GitHub 头像和昵称
- [ ] 登出后再次 GitHub 登录，直接进首页（走「已存在」分支）
- [ ] 微信扫码授权后，流程同上
