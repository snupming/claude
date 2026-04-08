// @ts-check
// Flat config — extends Nuxt's auto-generated ESLint config.
// See: https://eslint.nuxt.com/packages/module
//
// NOTE: 타입 인지(type-aware) 규칙(no-unsafe-*, no-floating-promises,
// no-misused-promises)은 vue-eslint-parser 와의 parserOptions.project
// 전달 설정이 필요하다. 추후 typed linting 설정 후 활성화.
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt({
  rules: {
    // === Type safety (non type-aware) ===
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/no-non-null-assertion': 'warn',
    '@typescript-eslint/consistent-type-assertions': [
      'error',
      { assertionStyle: 'as', objectLiteralTypeAssertions: 'never' },
    ],

    // === Async / empty blocks ===
    'require-await': 'error',
    'no-empty': ['error', { allowEmptyCatch: false }],
    '@typescript-eslint/no-unused-vars': [
      'error',
      { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
    ],

    // === Vue composable safety ===
    'vue/no-ref-as-operand': 'error',
    'vue/no-watch-after-await': 'error',
    'vue/no-async-in-computed-properties': 'error',
  },
})
