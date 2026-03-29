export default defineNuxtRouteMiddleware(async (to) => {
  const { isLoggedIn, fetchUser } = useAuth()

  // 최초 로드 시 서버/클라이언트에서 인증 상태 복원 (쿠키 기반)
  // useState는 새로고침마다 null로 초기화되므로 fetchUser 필요
  if (!isLoggedIn.value) {
    await fetchUser()
  }

  const publicPaths = ['/', '/login', '/signup', '/pricing', '/terms', '/privacy']
  const isPublicPath = publicPaths.includes(to.path) || to.path.startsWith('/blog')

  // Redirect to login if accessing protected route without auth
  if (!isPublicPath && !isLoggedIn.value) {
    return navigateTo('/login')
  }

  // Redirect to dashboard if accessing auth pages while logged in
  if ((to.path === '/login' || to.path === '/signup') && isLoggedIn.value) {
    return navigateTo('/dashboard')
  }
})
