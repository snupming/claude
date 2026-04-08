// @ts-check
// Flat config — extends Nuxt's auto-generated ESLint config.
// See: https://eslint.nuxt.com/packages/module
//
// NOTE: 타입 인지(type-aware) 규칙(no-unsafe-*, no-floating-promises,
// no-misused-promises)은 vue-eslint-parser 와의 parserOptions.project
// 전달 설정이 필요하다. 추후 typed linting 설정 후 활성화.
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt(
  // === Project-wide rules ===
  {
    rules: {
      // Type safety (non type-aware)
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'warn',
      '@typescript-eslint/consistent-type-assertions': [
        'error',
        { assertionStyle: 'as', objectLiteralTypeAssertions: 'never' },
      ],

      // Async / empty blocks
      'require-await': 'error',
      'no-empty': ['error', { allowEmptyCatch: false }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // Vue composable safety
      'vue/no-ref-as-operand': 'error',
      'vue/no-watch-after-await': 'error',
      'vue/no-async-in-computed-properties': 'error',

      // Vue 3 는 multi-root template 을 지원하므로 Vue 2 규칙 비활성화
      'vue/no-multiple-template-root': 'off',
    },
  },

  // === Nuxt server handlers: defineEventHandler(async ...) 는 await 없이
  //     백엔드 호출 결과를 직접 return 하는 패턴이 관용이다. ===
  {
    files: ['server/api/**/*.ts', 'server/utils/**/*.ts'],
    rules: {
      'require-await': 'off',
    },
  },

  // === Test files: 모의 객체/any 허용, vi.mock 호이스팅으로 import/first 불가 ===
  {
    files: [
      '**/__tests__/**/*.ts',
      '**/*.test.ts',
      '**/*.spec.ts',
    ],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/consistent-type-assertions': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
      'import/first': 'off',
      'import/order': 'off',
    },
  },

  // === shadcn-vue UI 컴포넌트: class/variant/size 등 optional prop 관용 ===
  {
    files: ['app/components/ui/**/*.vue'],
    rules: {
      'vue/require-default-prop': 'off',
    },
  },

  // === 스타일 경고 (HTML self-closing, attribute order, linebreak) 비활성 ===
  // 자동 포매팅은 prettier 영역. Vue 3 에서는 <img/> / <img> 모두 유효.
  {
    files: ['**/*.vue'],
    rules: {
      'vue/html-self-closing': 'off',
      'vue/first-attribute-linebreak': 'off',
      'vue/attributes-order': 'off',
    },
  },
)
