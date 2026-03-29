import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  devtools: { enabled: true },

  modules: ['shadcn-nuxt', '@nuxt/fonts'],

  shadcn: {
    prefix: '',
    componentDir: './app/components/ui',
  },

  css: ['~/assets/css/tailwind.css'],

  vite: {
    plugins: [tailwindcss()],
  },

  routeRules: {
    '/': { prerender: true },
    '/pricing': { prerender: true },
    '/login': { ssr: true },
    '/signup': { ssr: true },
    '/dashboard/**': { ssr: false },
  },
  // 컴포넌트 자동 import — 폴더명 접두사 제거
  components: [
    {
      path: '~/components',
      pathPrefix: false,
    },
  ],
})
