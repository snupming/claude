export default defineEventHandler(async (event) => {
  const refreshToken = getRefreshTokenFromCookie(event)

  if (refreshToken) {
    try {
      await backendFetch('/api/v1/auth/logout', {
        method: 'POST',
        body: { refreshToken },
      })
    }
    catch {
      // Ignore backend errors during logout
    }
  }

  clearAuthCookies(event)
  return { success: true }
})
