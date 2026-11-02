import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ESLint } from 'eslint'
const eslint = new ESLint()
for (const [name, source, rule] of [
 ['valid','<script setup lang="ts">const title: string = "ok"</script><template>{{ title }}</template>',null],
 ['any','<script setup lang="ts">const v: any = 1</script><template>{{ v }}</template>','@typescript-eslint/no-explicit-any'],
 ['key','<template><p v-for="x in [1]">{{ x }}</p></template>','vue/require-v-for-key'],
 ['if-for','<template><p v-for="x in [1]" v-if="true" :key="x">{{ x }}</p></template>','vue/no-use-v-if-with-v-for'],
 ['unused','<script setup lang="ts">const unused = 1</script><template>ok</template>','@typescript-eslint/no-unused-vars'],
 ['eval','<script setup lang="ts">eval("1")</script><template>ok</template>','no-eval'],
]) test(name, async () => {
 const [result] = await eslint.lintText(source,{filePath:'src/ComplianceExample.vue'})
 if(rule) assert.ok(result.messages.some(x=>x.ruleId===rule),JSON.stringify(result.messages))
 else assert.equal(result.errorCount,0,JSON.stringify(result.messages))
})
