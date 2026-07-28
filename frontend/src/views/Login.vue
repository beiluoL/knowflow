<template>
  <main class="auth-page">
    <section class="brand-panel">
      <div class="brand-grid">
        <div class="grid-line grid-line-1"></div>
        <div class="grid-line grid-line-2"></div>
        <div class="grid-line grid-line-3"></div>
      </div>
      <div class="brand-decoration">
        <div class="deco-shape deco-1"></div>
        <div class="deco-shape deco-2"></div>
        <div class="deco-shape deco-3"></div>
      </div>
      <div class="brand-content">
        <div class="brand-icon-wrapper">
          <Icon name="book-open" :size="48" />
        </div>
        <h1 class="brand-title">知识库</h1>
        <div class="brand-divider"></div>
        <p class="brand-slogan">构建你的知识体系<br>让学习更高效</p>
        <div class="brand-features">
          <div v-for="(feature, idx) in features" :key="feature" class="feature-item" :style="{ animationDelay: `${idx * 0.1}s` }">
            <Icon name="check" :size="16" />
            <span>{{ feature }}</span>
          </div>
        </div>
      </div>
      <p class="brand-copyright">© 2026 知识库. All rights reserved.</p>
    </section>
    <section class="form-panel">
      <div class="form-wrapper">
        <div class="mobile-brand">
          <div class="mobile-brand-icon">
            <Icon name="book-open" :size="28" />
          </div>
          <div class="mobile-brand-name">知识库</div>
        </div>
        <div class="tab-switch">
          <button
            class="tab-btn"
            :class="{ active: isLogin }"
            @click="isLogin = true"
          >
            登录
          </button>
          <button
            class="tab-btn"
            :class="{ active: !isLogin }"
            @click="isLogin = false"
          >
            注册
          </button>
        </div>
        <form v-if="isLogin" class="auth-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <label class="form-label" for="login-username">邮箱或手机号</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="mail" :size="16" />
              </span>
              <input
                id="login-username"
                v-model="loginForm.username"
                type="text"
                name="username"
                autocomplete="username"
                placeholder="请输入邮箱或手机号"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label" for="login-password">密码</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="lock" :size="16" />
              </span>
              <input
                id="login-password"
                v-model="loginForm.password"
                :type="showLoginPassword ? 'text' : 'password'"
                name="password"
                autocomplete="current-password"
                placeholder="请输入密码"
                class="form-input"
              />
              <button
                type="button"
                class="input-suffix"
                :aria-label="showLoginPassword ? '隐藏密码' : '显示密码'"
                @click="showLoginPassword = !showLoginPassword"
              >
                <Icon :name="showLoginPassword ? 'eye' : 'eye-off'" :size="16" />
              </button>
            </div>
          </div>
          <div class="form-row">
            <label class="checkbox-label">
              <input
                v-model="loginForm.rememberMe"
                type="checkbox"
                class="custom-checkbox"
              />
              <span>记住我</span>
            </label>
            <button type="button" class="link-text">忘记密码?</button>
          </div>
          <button
            type="submit"
            class="submit-btn"
            :disabled="loginLoading"
          >
            <span v-if="loginLoading" class="btn-spinner"></span>
            {{ loginLoading ? '登录中…' : '登录' }}
          </button>
          <div class="divider-section">
            <div class="divider-line"></div>
            <span class="divider-text">或使用以下方式登录</span>
            <div class="divider-line"></div>
          </div>
          <div class="social-buttons">
            <button type="button" class="social-btn" aria-label="使用微信登录" @click="loginWithWechat">
              <Icon name="message-circle" :size="18" />
            </button>
            <button type="button" class="social-btn" aria-label="使用 GitHub 登录" @click="loginWithGithub">
              <Icon name="github" :size="18" />
            </button>
          </div>
          <p class="switch-text">
            还没有账号？<button type="button" class="link-btn" @click="isLogin = false">立即注册</button>
          </p>
        </form>
        <form v-else class="auth-form" @submit.prevent="handleRegister">
          <div class="form-group">
            <label class="form-label" for="register-username">用户名</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="user" :size="16" />
              </span>
              <input
                id="register-username"
                v-model="registerForm.username"
                type="text"
                name="username"
                autocomplete="username"
                placeholder="请输入用户名"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-email">邮箱</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="mail" :size="16" />
              </span>
              <input
                id="register-email"
                v-model="registerForm.email"
                type="email"
                name="email"
                autocomplete="email"
                placeholder="请输入邮箱地址"
                class="form-input"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-password">密码</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="lock" :size="16" />
              </span>
              <input
                id="register-password"
                v-model="registerForm.password"
                :type="showRegisterPassword ? 'text' : 'password'"
                name="new-password"
                autocomplete="new-password"
                placeholder="请设置密码（至少8位）"
                class="form-input"
              />
              <button
                type="button"
                class="input-suffix"
                :aria-label="showRegisterPassword ? '隐藏密码' : '显示密码'"
                @click="showRegisterPassword = !showRegisterPassword"
              >
                <Icon :name="showRegisterPassword ? 'eye' : 'eye-off'" :size="16" />
              </button>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label" for="register-confirm-password">确认密码</label>
            <div class="input-group">
              <span class="input-prefix">
                <Icon name="lock" :size="16" />
              </span>
              <input
                id="register-confirm-password"
                v-model="registerForm.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                name="confirm-password"
                autocomplete="new-password"
                placeholder="请再次输入密码"
                class="form-input"
              />
              <button
                type="button"
                class="input-suffix"
                :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
                @click="showConfirmPassword = !showConfirmPassword"
              >
                <Icon :name="showConfirmPassword ? 'eye' : 'eye-off'" :size="16" />
              </button>
            </div>
          </div>
          <label class="agreement-label">
            <input
              v-model="registerForm.agreed"
              type="checkbox"
              class="custom-checkbox"
            />
            <span>
              我已阅读并同意
              <button type="button" class="link-text">服务协议</button>
              和
              <button type="button" class="link-text">隐私政策</button>
            </span>
          </label>
          <button
            type="submit"
            class="submit-btn"
            :disabled="registerLoading"
          >
            <span v-if="registerLoading" class="btn-spinner"></span>
            {{ registerLoading ? '注册中…' : '注册' }}
          </button>
          <p class="switch-text">
            已有账号？<button type="button" class="link-btn" @click="isLogin = true">立即登录</button>
          </p>
        </form>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import Icon from '@/components/ui/Icon.vue';
import { getApiError, notify } from '@/utils/toast';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const isLogin = ref(route.query.tab !== 'register');
const loginLoading = ref(false);
const registerLoading = ref(false);
const showLoginPassword = ref(false);
const showRegisterPassword = ref(false);
const showConfirmPassword = ref(false);

const features = ['AI 智能问答', '个性化学习路径', '间隔重复记忆'];

const loginForm = ref({
  username: '',
  password: '',
  rememberMe: false,
});

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  agreed: false,
});

async function handleLogin(): Promise<void> {
  if (!loginForm.value.username || !loginForm.value.password) {
    notify('请填写用户名和密码', 'warning');
    return;
  }
  loginLoading.value = true;
  try {
    await auth.login({
      username: loginForm.value.username,
      password: loginForm.value.password,
    });
    const redirect = (route.query.redirect as string) || '/';
    router.push(redirect);
  } catch (e: unknown) {
    notify(getApiError(e, '登录失败，请检查用户名或密码'), 'info');
  } finally {
    loginLoading.value = false;
  }
}

function loginWithGithub(): void {
  window.location.href = '/api/auth/oauth/github'
}

function loginWithWechat(): void {
  window.location.href = '/api/auth/oauth/wechat'
}

async function handleRegister(): Promise<void> {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    notify('请填写完整信息', 'warning');
    return;
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    notify('两次输入的密码不一致', 'warning');
    return;
  }
  if (!registerForm.value.agreed) {
    notify('请阅读并同意服务协议和隐私政策', 'warning');
    return;
  }
  registerLoading.value = true;
  try {
    await auth.register({
      username: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
      nickname: registerForm.value.username,
    });
    notify('注册成功，已自动登录！', 'success');
    router.push('/');
  } catch (e: unknown) {
    notify(getApiError(e, '注册失败，请稍后再试'), 'info');
  } finally {
    registerLoading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
}

.brand-panel {
  display: none;
  position: relative;
  width: 55%;
  min-height: 100vh;
  padding: 60px 80px;
  background: linear-gradient(145deg, var(--kb-primary) 0%, #4A7DE8 50%, #3B6FE0 100%);
  overflow: hidden;
  animation: slideInLeft 0.8s ease-out;
}

@keyframes slideInLeft {
  from { transform: translateX(-30px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.brand-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.grid-line {
  position: absolute;
  background: rgba(255, 255, 255, 0.06);
}

.grid-line-1 {
  top: 20%;
  left: 0;
  right: 0;
  height: 1px;
}

.grid-line-2 {
  top: 40%;
  left: 0;
  right: 0;
  height: 1px;
}

.grid-line-3 {
  top: 60%;
  left: 0;
  right: 0;
  height: 1px;
}

.brand-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.deco-shape {
  position: absolute;
  background: rgba(255, 255, 255, 0.08);
}

.deco-1 {
  top: -50px;
  right: -50px;
  width: 300px;
  height: 300px;
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  animation: float 8s ease-in-out infinite;
}

.deco-2 {
  bottom: -80px;
  left: -80px;
  width: 250px;
  height: 250px;
  border-radius: 60% 40% 30% 70% / 60% 30% 70% 40%;
  animation: float 10s ease-in-out infinite reverse;
}

.deco-3 {
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.05) 0%, transparent 70%);
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-20px) rotate(5deg); }
}

.brand-content {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
}

.brand-icon-wrapper {
  width: 88px;
  height: 88px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  margin-bottom: 40px;
  animation: fadeInUp 0.6s ease-out 0.1s both;
}

@keyframes fadeInUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.brand-title {
  font-family: var(--font-serif);
  font-size: 56px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.04em;
  line-height: 1.1;
  margin-bottom: 24px;
  animation: fadeInUp 0.6s ease-out 0.2s both;
}

.brand-divider {
  width: 60px;
  height: 3px;
  background: rgba(255, 255, 255, 0.8);
  margin-bottom: 32px;
  animation: fadeInUp 0.6s ease-out 0.3s both;
}

.brand-slogan {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
  margin-bottom: 56px;
  animation: fadeInUp 0.6s ease-out 0.4s both;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.9);
  animation: fadeInUp 0.5s ease-out both;
}

.feature-item svg {
  color: rgba(255, 255, 255, 0.9);
}

.brand-copyright {
  position: absolute;
  bottom: 40px;
  left: 80px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
  background: var(--kb-card);
  animation: slideInRight 0.8s ease-out;
}

@keyframes slideInRight {
  from { transform: translateX(30px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.form-wrapper {
  width: 100%;
  max-width: 420px;
}

.mobile-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 48px;
  animation: fadeInUp 0.5s ease-out 0.3s both;
}

.mobile-brand-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: var(--kb-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--kb-primary-foreground);
  margin-bottom: 16px;
}

.mobile-brand-name {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
  color: var(--kb-foreground);
}

.tab-switch {
  display: flex;
  margin-bottom: 40px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--kb-border);
  animation: fadeInUp 0.5s ease-out 0.4s both;
}

.tab-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: none;
  cursor: pointer;
  position: relative;
  transition: color 0.2s ease;
}

.tab-btn:hover {
  color: var(--kb-foreground);
}

.tab-btn.active {
  color: var(--kb-primary);
  font-weight: 600;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--kb-primary);
  animation: slideInX 0.3s ease-out;
}

@keyframes slideInX {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-group {
  animation: fadeInUp 0.5s ease-out both;
}

.form-group:nth-child(1) { animation-delay: 0.5s; }
.form-group:nth-child(2) { animation-delay: 0.55s; }
.form-group:nth-child(3) { animation-delay: 0.6s; }
.form-group:nth-child(4) { animation-delay: 0.65s; }
.form-group:nth-child(5) { animation-delay: 0.7s; }

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
  margin-bottom: 10px;
}

.input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.input-prefix {
  position: absolute;
  left: 16px;
  color: var(--kb-muted-foreground);
  pointer-events: none;
}

.form-input {
  width: 100%;
  height: 48px;
  padding: 0 48px;
  font-size: 15px;
  border-radius: var(--kb-radius-md);
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  color: var(--kb-foreground);
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  font-family: inherit;
}

.form-input::placeholder {
  color: var(--kb-muted-foreground);
}

.form-input:focus {
  border-color: var(--kb-primary);
  box-shadow: 0 0 0 3px rgba(59, 111, 224, 0.08);
}

.form-input:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
}

.input-suffix {
  position: absolute;
  right: 16px;
  background: transparent;
  border: none;
  color: var(--kb-muted-foreground);
  cursor: pointer;
  padding: 0;
  transition: color 0.2s ease;
}

.input-suffix:hover {
  color: var(--kb-foreground);
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  animation: fadeInUp 0.5s ease-out 0.75s both;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--kb-muted-foreground);
}

.custom-checkbox {
  width: 18px;
  height: 18px;
  accent-color: var(--kb-primary);
  cursor: pointer;
  border-radius: 4px;
}

.link-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-primary);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  text-decoration: none;
}

.link-text:hover {
  text-decoration: underline;
}

.link-text:focus-visible {
  outline: 2px solid var(--kb-primary);
  outline-offset: 2px;
  border-radius: 2px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--kb-radius-md);
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  transition: all 0.2s ease;
  animation: fadeInUp 0.5s ease-out 0.8s both;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 111, 224, 0.3);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.divider-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 8px 0;
  animation: fadeInUp 0.5s ease-out 0.85s both;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: var(--kb-border);
}

.divider-text {
  font-size: 13px;
  color: var(--kb-muted-foreground);
  white-space: nowrap;
}

.social-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  animation: fadeInUp 0.5s ease-out 0.9s both;
}

.social-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--kb-background);
  border: 1px solid var(--kb-border);
  color: var(--kb-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.social-btn:hover {
  background: var(--kb-primary);
  color: var(--kb-primary-foreground);
  border-color: var(--kb-primary);
  transform: translateY(-2px);
}

.switch-text {
  text-align: center;
  font-size: 14px;
  color: var(--kb-muted-foreground);
  margin-top: 8px;
  animation: fadeInUp 0.5s ease-out 0.95s both;
}

.link-btn {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-primary);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}

.link-btn:hover {
  text-decoration: underline;
}

.agreement-label {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--kb-muted-foreground);
  line-height: 1.6;
  animation: fadeInUp 0.5s ease-out 0.75s both;
}

.agreement-label .custom-checkbox {
  margin-top: 2px;
}

@media (min-width: 1024px) {
  .brand-panel { display: flex; }
  .mobile-brand { display: none; }
}

@media (max-width: 1023px) {
  .brand-panel {
    display: none;
  }
  .form-panel {
    padding: 48px 24px;
    min-height: 100vh;
  }
}
</style>
