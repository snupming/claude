/**
 * Dev-only: 첫 요청이 들어오면 나머지 SSR 페이지를 백그라운드 warmup.
 * Vite on-demand 컴파일이 미리 완료되어 후속 페이지 접속이 빨라진다.
 */
export default defineNitroPlugin((nitro) => {
  if (!import.meta.dev) return

  const warmupPaths = ['/', '/login']
  let warmedUp = false

  nitro.hooks.hook('request', (event) => {
    if (warmedUp) return
    warmedUp = true

    const url = getRequestURL(event)
    const origin = url.origin

    // 현재 요청이 아닌 다른 페이지들을 백그라운드로 warmup
    for (const path of warmupPaths) {
      if (url.pathname !== path) {
        fetch(`${origin}${path}`).catch(() => {})
      }
    }
  })
})
