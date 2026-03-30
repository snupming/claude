export default defineEventHandler(async (event) => {
  const body = await readBody(event)

  // 1. 회원가입 (토큰 미반환)
  await backendFetch<{
    id: string
    name: string
    email: string
    role: string
  }>('/api/v1/auth/signup', {
    method: 'POST',
    body,
  })

  // 2. 자동 로그인
  const loginData = await backendFetch<{
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
    body: { email: body.email, password: body.password },
  })

  setAuthCookies(event, {
    accessToken: loginData.accessToken,
    refreshToken: loginData.refreshToken,
  })

  return { user: loginData.user }
})
