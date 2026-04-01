import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  devtools: { enabled: true },

  modules: ['shadcn-nuxt'],

  app: {
    head: {
      link: [
        {
          rel: 'stylesheet',
          href: 'https://cdn.jsdelivr.net/gh/wanteddev/wanted-sans@v1.0.3/packages/wanted-sans/fonts/webfonts/variable/split/WantedSansVariable.min.css',
        },
      ],
      script: [
        {
          innerHTML: `(function(){var t=localStorage.getItem('ownpic-theme');var s=!t||t==='system';var d=s?window.matchMedia('(prefers-color-scheme: dark)').matches:t==='dark';document.documentElement.setAttribute('data-theme',d?'dark':'light')})()`,
          type: 'text/javascript',
        },
      ],
    },
  },

  shadcn: {
    prefix: '',
    componentDir: './app/components/ui',
  },

  runtimeConfig: {
    backendUrl: 'http://localhost:8080',
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
    '/protect/**': { ssr: false },
    '/detect/**': { ssr: false },
    '/notifications': { ssr: false },
  },
  // 컴포넌트 자동 import — 폴더명 접두사 제거
  components: [
    {
      path: '~/components',
      pathPrefix: false,
    },
  ],
})
