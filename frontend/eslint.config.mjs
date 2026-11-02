import vue from 'eslint-plugin-vue'
import ts from 'typescript-eslint'
export default [
  ...vue.configs['flat/essential'],
  { files: ['src/**/*.{ts,tsx,vue,js}'], languageOptions: { parserOptions: { parser: ts.parser } }, plugins: { '@typescript-eslint': ts.plugin }, rules: {
    'no-unused-vars': 'off', 'no-undef': 'off', // vue-tsc owns TypeScript name resolution.
    '@typescript-eslint/no-unused-vars': 'error', '@typescript-eslint/no-explicit-any': 'error',
    eqeqeq: ['error', 'always', { null: 'ignore' }], 'no-eval': 'error', 'no-debugger': 'error',
    'vue/multi-word-component-names': 'error', 'vue/require-v-for-key': 'error', 'vue/no-use-v-if-with-v-for': 'error',
  } },
  { files: ['src/**/*.{ts,tsx}'], languageOptions: { parser: ts.parser } },
]
