import type {RouteRecordRaw} from 'vue-router'
// 构建期 DEV 防护：生产环境不注册或导入此懒加载模块。
export function createDevRoutes(enabled:boolean):RouteRecordRaw[]{return enabled?[{path:'/dev/ui-components',name:'UIComponents',component:()=>import('@/views/dev/UIComponents.vue'),meta:{layout:'none',backgroundOverride:'none'}}]:[]}
