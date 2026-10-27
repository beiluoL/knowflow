// 验收使用独立依赖缓存，避免与现有开发实例互相刷新预构建资源。
import {mergeConfig} from 'vite'
import config from '../vite.config'
export default mergeConfig(config,{cacheDir:'/private/tmp/knowflow-notes-v2-vite',optimizeDeps:{entries:['index.html']}})
