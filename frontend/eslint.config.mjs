// @ts-check
// Flat config — extends Nuxt's auto-generated ESLint config.
// See: https://eslint.nuxt.com/packages/module
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt({
  rules: {
    // === Type safety ===
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/no-non-null-assertion': 'warn',
    '@typescript-eslint/consistent-type-assertions': [
      'error',
      { assertionStyle: 'as', objectLiteralTypeAssertions: 'never' },
    ],
    '@typescript-eslint/no-unsafe-assignment': 'error',
    '@typescript-eslint/no-unsafe-member-access': 'error',
    '@typescript-eslint/no-unsafe-call': 'error',
    '@typescript-eslint/no-unsafe-return': 'error',

    // === Promise / async ===
    '@typescript-eslint/no-floating-promises': 'error',
    '@typescript-eslint/no-misused-promises': 'error',
    'require-await': 'error',

    // === Empty catch / unused vars ===
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
