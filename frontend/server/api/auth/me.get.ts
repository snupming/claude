import { z } from 'zod'

const JwtPayload = z.object({
  sub: z.string(),
  email: z.string(),
  role: z.string(),
})

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
    const raw: unknown = JSON.parse(atob(parts[1]))
    const parsed = JwtPayload.safeParse(raw)
    if (!parsed.success) {
      throw createError({ statusCode: 401, data: { detail: '유효하지 않은 토큰입니다.' } })
    }

    return {
      user: {
        id: parsed.data.sub,
        email: parsed.data.email,
        role: parsed.data.role,
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
