// 全局「庆祝」反馈系统：用于获得数字证书等里程碑时刻弹出游戏化的全屏庆祝弹窗。
// 与 toast.ts 同构：reactive 全局状态 + 函数式 API，由 App.vue 挂载的 Celebration.vue 宿主渲染。
import { reactive } from 'vue';

/** 证书庆祝事件的载荷（对应后端 LearningCertificateVO 的关键展示字段）。 */
export interface CelebrationCertificate {
  id: number;
  pathTitle?: string;
  userName?: string;
  certNo?: string;
  issueDate?: string;
}

export interface CelebrationItem {
  id: number;
  /** 里程碑类型：目前仅 CERTIFICATE（获得证书）。 */
  type: 'CERTIFICATE';
  cert: CelebrationCertificate;
}

export const celebrateState = reactive({
  items: [] as CelebrationItem[],
});

let seq = 0;

/**
 * 触发「获得证书」全屏庆祝。
 * @param cert 证书信息（id 必填，用于「查看证书」跳转）。
 */
export function celebrateCertificate(cert: CelebrationCertificate): void {
  const id = ++seq;
  celebrateState.items.push({ id, type: 'CERTIFICATE', cert });
}

/** 关闭指定 id 的庆祝弹窗；不传 id 则关闭最新一条。 */
export function dismissCelebration(id?: number): void {
  if (id === undefined) {
    celebrateState.items.pop();
    return;
  }
  const idx = celebrateState.items.findIndex((c) => c.id === id);
  if (idx !== -1) celebrateState.items.splice(idx, 1);
}
