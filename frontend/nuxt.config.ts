import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
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
})
