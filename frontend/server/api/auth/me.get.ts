export default defineEventHandler(async (event) => {
  const accessToken = getAccessTokenFromCookie(event)

  if (!accessToken) {
    throw createError({
      statusCode: 401,
      data: { detail: '인증이 필요합니다.' },
    })
  }

  // Decode JWT payload to get user info (without verifying - backend already verified during issuance)
  try {
    const parts = accessToken.split('.')
    if (!parts[1]) {
      throw createError({ statusCode: 401, data: { detail: '유효하지 않은 토큰입니다.' } })
    }
    const payload = JSON.parse(atob(parts[1]))

    return {
      user: {
        id: payload.sub,
        email: payload.email,
        role: payload.role,
      },
    }
  }
  catch {
    throw createError({
      statusCode: 401,
      data: { detail: '유효하지 않은 토큰입니다.' },
    })
  }
})
