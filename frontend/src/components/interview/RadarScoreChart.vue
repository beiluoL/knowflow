<script setup lang="ts">
import { computed } from 'vue'
import type { RadarDomain } from '@/api/radarStudy'
const props=defineProps<{domains:RadarDomain[]}>()
const axes=computed(()=>props.domains.map((domain,i)=>{const angle=i/Math.max(1,props.domains.length)*Math.PI*2-Math.PI/2;return {...domain,x:Math.cos(angle),y:Math.sin(angle)}}))
const measured=computed(()=>axes.value.filter(d=>d.currentAverage!=null))
const polygon=computed(()=>axes.value.map(d=>`${240+d.x*(d.currentAverage??0)*15},${240+d.y*(d.currentAverage??0)*15}`).join(' '))
</script>
<template>
 <figure class="radar-score-chart">
  <svg viewBox="0 0 480 480" role="img" aria-label="本人面试训练成绩雷达，未测领域不计零分">
   <g v-for="radius in [30,60,90,120,150]" :key="radius"><circle cx="240" cy="240" :r="radius" class="radar-ring"/><text x="244" :y="240-radius" class="radar-tick">{{radius/15}}</text></g>
   <g v-for="axis in axes" :key="axis.domain"><line x1="240" y1="240" :x2="240+axis.x*150" :y2="240+axis.y*150" class="radar-ring"/><text :x="240+axis.x*190" :y="240+axis.y*190" text-anchor="middle" dominant-baseline="middle" class="radar-label">{{axis.domain}}<tspan :x="240+axis.x*190" dy="16">{{axis.currentAverage==null?(axis.total?'未测':'待补充'):axis.currentAverage.toFixed(1)}}</tspan></text></g>
   <polygon v-if="axes.length&&measured.length===axes.length" :points="polygon" class="radar-area"/>
   <circle v-for="axis in measured" :key="axis.domain" :cx="240+axis.x*axis.currentAverage!*15" :cy="240+axis.y*axis.currentAverage!*15" r="5" class="radar-measure" tabindex="0"><title>{{axis.domain}}：{{axis.currentAverage}} / 10；已答{{axis.answered}}/{{axis.total}}题</title></circle>
  </svg>
  <figcaption>{{measured.length?'蓝点来自实际AI训练评价；空白轴表示未测，不补0分。':'尚无AI训练评分，未测领域不画为0分。'}}<br>此图不等同于系统客观掌握度。</figcaption>
 </figure>
</template>
<style scoped>
.radar-score-chart{margin:0;min-width:0}.radar-score-chart svg{display:block;width:100%;height:auto}.radar-ring{fill:none;stroke:var(--kf-border);stroke-width:1}.radar-label{fill:var(--kf-foreground);font-size:var(--kf-fs-body-sm)}.radar-tick{fill:var(--kf-muted-foreground);font-size:var(--kf-fs-caption)}.radar-measure{fill:var(--kf-primary);stroke:var(--kf-card);stroke-width:2}.radar-measure:focus-visible{outline:var(--kf-studio-focus) solid var(--kf-ring)}.radar-area{fill:var(--kf-primary);fill-opacity:.12;stroke:var(--kf-primary);stroke-width:2}figcaption{font-size:var(--kf-fs-caption);color:var(--kf-muted-foreground);line-height:var(--kf-lh-body-sm)}
</style>
