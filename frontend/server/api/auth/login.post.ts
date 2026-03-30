export default defineEventHandler(async (event) => {
  const body = await readBody(event)

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
  }>('/api/v1/auth/login', {
    method: 'POST',
    body,
  })

  setAuthCookies(event, {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
  })

  return { user: data.user }
})
