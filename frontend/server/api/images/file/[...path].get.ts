export default defineEventHandler(async (event) => {
  const path = getRouterParam(event, 'path')

  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, statusMessage: '인증이 필요합니다.' })
  }

  const backendUrl = useRuntimeConfig().backendUrl as string

  const response = await fetch(`${backendUrl}/api/v1/images/file/${path}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  const contentType = response.headers.get('content-type') || 'image/png'
  const buffer = await response.arrayBuffer()

  setResponseHeader(event, 'content-type', contentType)
  setResponseHeader(event, 'cache-control', 'public, max-age=3600')

  return Buffer.from(buffer)
})
