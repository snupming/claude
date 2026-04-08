export default defineEventHandler(async (event) => {
  const body = await readBody(event)

  let data
  try {
    data = await backendFetch<{
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
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'statusCode' in err) throw err
    throw createError({
      statusCode: 503,
      statusMessage: 'Service Unavailable',
      data: { detail: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' },
    })
  }

  setAuthCookies(event, {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
  })

  return { user: data.user }
})
