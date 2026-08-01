<template>
  <div class="certificate-page">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <button class="back-btn" @click="router.back()">
        <Icon name="arrow-left" :size="18" />
        <span>返回</span>
      </button>
      <h1 class="page-title">数字证书</h1>
      <button class="download-btn" :disabled="!cert" @click="downloadImage">
        <Icon name="download" :size="16" />
        <span>下载证书</span>
      </button>
    </div>

    <!-- 证书主体 -->
    <div v-if="loading" class="loading-state">
      <div class="h-6 w-40 rounded animate-pulse" style="background: var(--kb-muted);"></div>
      <div class="h-[360px] w-full max-w-2xl rounded-xl animate-pulse mt-6" style="background: var(--kb-muted);"></div>
    </div>

    <div v-else-if="cert" class="cert-body">
      <div class="cert-viewer" :class="{ 'is-mine': cert.mine }">
        <canvas ref="certCanvas" :width="CERT_W" :height="CERT_H" class="cert-canvas"></canvas>
      </div>

      <!-- 证书信息 -->
      <div class="cert-info-card">
        <div class="info-row">
          <span class="info-label">证书编号</span>
          <span class="info-value mono">{{ cert.certNo }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">持证人</span>
          <span class="info-value">{{ cert.userName }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">完成路径</span>
          <span class="info-value">{{ cert.pathTitle }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">颁发日期</span>
          <span class="info-value">{{ formatDate(cert.issueDate) }}</span>
        </div>
      </div>
    </div>

    <p v-else class="empty-state">
      <Icon name="award" :size="40" style="color: var(--kb-muted-foreground);" />
      证书不存在或已被移除
    </p>

    <!-- 验证区 -->
    <div class="verify-section">
      <h3 class="verify-title">验证证书真伪</h3>
      <div class="verify-box">
        <input
          v-model="verifyNo"
          type="text"
          placeholder="输入证书验证码（如 KC-20260731-1234）"
          class="verify-input"
        />
        <button class="verify-btn" :disabled="verifying" @click="doVerify">
          <Icon name="shield" :size="16" />
          <span>{{ verifying ? '验证中...' : '验证' }}</span>
        </button>
      </div>
      <div v-if="verifyResult" class="verify-result" :class="verifyResult.ok ? 'verify-ok' : 'verify-fail'">
        <Icon :name="verifyResult.ok ? 'check' : 'x'" :size="16" />
        <span>{{ verifyResult.msg }}</span>
        <router-link v-if="verifyResult.certId" :to="`/certificate/${verifyResult.certId}`" class="verify-link">
          查看证书
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 数字证书页（G-CERT-01）：
 * 展示路径完成后自动颁发的数字证书，支持 canvas 生成证书图并下载 PNG，附带验证码核验功能。
 */
import { ref, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from '@/components/ui/Icon.vue';
import { learningApi } from '@/api';
import { notify } from '@/utils/toast';
import type { LearningCertificateVO } from '@/api/types';

const route = useRoute();
const router = useRouter();

const certCanvas = ref<HTMLCanvasElement | null>(null);
const cert = ref<LearningCertificateVO | null>(null);
const loading = ref(false);

const CERT_W = 1200;
const CERT_H = 850;
const PRIMARY = '#3B6FE0';
const ACCENT = '#10B981';

const formatDate = (d?: string) => {
  if (!d) return '';
  const date = new Date(d);
  if (Number.isNaN(date.getTime())) return d;
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
};

// ===== 验证区 =====
const verifyNo = ref('');
const verifying = ref(false);
const verifyResult = ref<{ ok: boolean; msg: string; certId?: number } | null>(null);

const doVerify = async () => {
  const no = verifyNo.value.trim();
  if (!no) {
    notify('请输入证书验证码', 'warning');
    return;
  }
  verifying.value = true;
  verifyResult.value = null;
  try {
    const result = await learningApi.verifyCertificate(no);
    verifyResult.value = {
      ok: true,
      certId: result.id,
      msg: `证书有效：${result.userName} 完成了《${result.pathTitle}》，颁发于 ${formatDate(result.issueDate)}`,
    };
  } catch {
    verifyResult.value = { ok: false, msg: '未找到该证书，请核对验证码' };
  } finally {
    verifying.value = false;
  }
};

// ===== 证书图片绘制与下载 =====
const drawCertificate = (canvas: HTMLCanvasElement, data: LearningCertificateVO) => {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const W = CERT_W;
  const H = CERT_H;

  // 背景
  const grad = ctx.createLinearGradient(0, 0, W, H);
  grad.addColorStop(0, '#f8fafc');
  grad.addColorStop(1, '#eef2f7');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, W, H);

  // 外边框
  ctx.strokeStyle = PRIMARY;
  ctx.lineWidth = 6;
  ctx.strokeRect(28, 28, W - 56, H - 56);
  ctx.strokeStyle = 'rgba(59, 111, 224, 0.35)';
  ctx.lineWidth = 2;
  ctx.strokeRect(44, 44, W - 88, H - 88);

  // 顶部徽章（金色奖章）
  const cx = W / 2;
  ctx.save();
  ctx.shadowColor = 'rgba(180, 140, 20, 0.4)';
  ctx.shadowBlur = 18;
  ctx.fillStyle = '#f5b940';
  ctx.beginPath();
  ctx.arc(cx, 150, 56, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 40px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('★', cx, 150);

  // 标题
  ctx.fillStyle = '#1e293b';
  ctx.font = '600 52px sans-serif';
  ctx.fillText('数 字 证 书', cx, 290);
  ctx.fillStyle = '#64748b';
  ctx.font = '26px sans-serif';
  ctx.fillText('CERTIFICATE OF COMPLETION', cx, 340);

  // 持证人
  ctx.fillStyle = '#475569';
  ctx.font = '24px sans-serif';
  ctx.fillText('兹证明', cx, 420);
  ctx.fillStyle = '#111827';
  ctx.font = 'bold 46px sans-serif';
  ctx.fillText(data.userName || '学员', cx, 480);

  // 完成路径
  ctx.fillStyle = '#475569';
  ctx.font = '24px sans-serif';
  ctx.fillText('已成功完成学习路径', cx, 545);
  ctx.fillStyle = PRIMARY;
  ctx.font = 'bold 34px sans-serif';
  ctx.fillText(data.pathTitle || '', cx, 600);

  // 颁发日期与验证码
  ctx.fillStyle = '#64748b';
  ctx.font = '20px sans-serif';
  ctx.fillText(`颁发日期：${formatDate(data.issueDate)}`, cx, 670);
  ctx.fillStyle = ACCENT;
  ctx.font = '22px monospace';
  ctx.fillText(`验证码：${data.certNo || ''}`, cx, 720);
};

const downloadImage = () => {
  const canvas = certCanvas.value;
  if (!canvas || !cert.value) return;
  // 先重新绘制（确保最新数据）
  drawCertificate(canvas, cert.value);
  const link = document.createElement('a');
  link.download = `证书-${cert.value.pathTitle || cert.value.certNo}.png`;
  link.href = canvas.toDataURL('image/png');
  link.click();
  notify('证书已下载', 'success');
};

const loadCert = async () => {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    const data = await learningApi.certificateDetail(id);
    cert.value = data;
    await nextTick();
    if (certCanvas.value) {
      drawCertificate(certCanvas.value, data);
    }
  } catch {
    cert.value = null;
  } finally {
    loading.value = false;
  }
};

onMounted(loadCert);
</script>

<style scoped>
.certificate-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: var(--kb-muted-foreground);
  background: transparent;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.back-btn:hover {
  background: var(--kb-muted);
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--kb-foreground);
}
.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.download-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.download-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  gap: 16px;
  color: var(--kb-muted-foreground);
}
.cert-viewer {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}
.cert-canvas {
  width: 100%;
  height: auto;
  display: block;
}
.cert-info-card {
  margin-top: 24px;
  padding: 20px;
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  background: var(--kb-card);
}
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed var(--kb-border);
}
.info-row:last-child {
  border-bottom: none;
}
.info-label {
  font-size: 14px;
  color: var(--kb-muted-foreground);
}
.info-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-foreground);
}
.info-value.mono {
  font-family: 'Fira Code', 'Consolas', monospace;
}
.verify-section {
  margin-top: 24px;
}
.verify-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-foreground);
  margin-bottom: 12px;
}
.verify-box {
  display: flex;
  gap: 12px;
}
.verify-input {
  flex: 1;
  padding: 10px 14px;
  font-size: 14px;
  color: var(--kb-foreground);
  background: var(--kb-card);
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  outline: none;
  transition: border-color 0.15s ease;
}
.verify-input:focus {
  border-color: var(--kb-primary);
}
.verify-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--kb-primary-foreground);
  background: var(--kb-primary);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.verify-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.verify-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.verify-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px 14px;
  font-size: 13px;
  border-radius: 8px;
}
.verify-ok {
  color: #047857;
  background: rgba(16, 185, 129, 0.1);
}
.verify-fail {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.1);
}
.verify-link {
  margin-left: auto;
  color: var(--kb-primary);
  text-decoration: none;
  font-weight: 500;
}
.verify-link:hover {
  text-decoration: underline;
}
</style>
