export default defineEventHandler(async (event) => {
  const refreshToken = getRefreshTokenFromCookie(event)

  if (!refreshToken) {
    throw createError({
      statusCode: 401,
      data: { detail: '인증이 필요합니다.' },
    })
  }

  const data = await backendFetch<{
    accessToken: string
    refreshToken: string
    user: {
      id: string
      name: string
      email: string
      role: string
      imageQuota: number
      imagesUsed: number
    }
  }>('/api/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
  })

  setAuthCookies(event, {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
  })

  return { user: data.user }
})
