import type { H3Event } from 'h3'

interface BackendRequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
}

function getBackendUrl(): string {
  return useRuntimeConfig().backendUrl as string
}

/**
 * 동적 값을 URL path 에 안전하게 결합한다.
 * server/api/* 에서 라우트 파라미터(id, path 등)를 백엔드 URL 에 보간할 때 반드시 사용.
 */
export function safeJoin(...segments: (string | number)[]): string {
  return segments.map(s => encodeURIComponent(String(s))).join('/')
}

export async function backendFetch<T>(path: string, options: BackendRequestOptions = {}): Promise<T> {
  const url = `${getBackendUrl()}${path}`

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

  // 204 No Content 등 빈 응답 처리
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }

  const text = await response.text()
  if (!text) return undefined as T

  return JSON.parse(text) as T
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
  const isProduction = !import.meta.dev

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

export async function authedBackendFetch<T>(event: H3Event, path: string, options: BackendRequestOptions = {}): Promise<T> {
  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, statusMessage: '인증이 필요합니다.' })
  }

  return backendFetch<T>(path, {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  })
}
