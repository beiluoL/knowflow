import type {RouteRecordRaw} from 'vue-router'
// Build-time DEV guard: the lazy module is not registered or imported in production.
export function createDevRoutes(enabled:boolean):RouteRecordRaw[]{return enabled?[{path:'/dev/ui-components',name:'UIComponents',component:()=>import('@/views/dev/UIComponents.vue'),meta:{layout:'none',backgroundOverride:'none'}}]:[]}
