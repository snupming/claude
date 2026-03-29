import type { H3Event } from 'h3'

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080'

interface BackendRequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
}

export async function backendFetch<T>(path: string, options: BackendRequestOptions = {}): Promise<T> {
  const url = `${BACKEND_URL}${path}`

  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...(options.body ? { body: JSON.stringify(options.body) } : {}),
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: '서버 오류가 발생했습니다.' }))
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data: error,
    })
  }

  return response.json() as Promise<T>
}

export function getAccessTokenFromCookie(event: H3Event): string | undefined {
  return getCookie(event, 'access_token')
}

export function getRefreshTokenFromCookie(event: H3Event): string | undefined {
  return getCookie(event, 'refresh_token')
}

interface AuthTokens {
  accessToken: string
  refreshToken: string
}

export function setAuthCookies(event: H3Event, tokens: AuthTokens) {
  const isProduction = process.env.NODE_ENV === 'production'

  setCookie(event, 'access_token', tokens.accessToken, {
    httpOnly: true,
    secure: isProduction,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 15, // 15 min
  })

  setCookie(event, 'refresh_token', tokens.refreshToken, {
    httpOnly: true,
    secure: isProduction,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24 * 7, // 7 days
  })
}

export function clearAuthCookies(event: H3Event) {
  deleteCookie(event, 'access_token', { path: '/' })
  deleteCookie(event, 'refresh_token', { path: '/' })
}
