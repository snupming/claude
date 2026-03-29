export default defineNuxtRouteMiddleware((to) => {
  const { isLoggedIn } = useAuth()

  const publicPaths = ['/', '/login', '/signup', '/pricing', '/terms', '/privacy']
  const isPublicPath = publicPaths.includes(to.path)

  // Redirect to login if accessing protected route without auth
  if (!isPublicPath && !isLoggedIn.value) {
    return navigateTo('/login')
  }

  // Redirect to dashboard if accessing auth pages while logged in
  if ((to.path === '/login' || to.path === '/signup') && isLoggedIn.value) {
    return navigateTo('/dashboard')
  }
})
