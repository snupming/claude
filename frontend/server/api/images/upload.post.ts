import { getAccessTokenFromCookie } from '~/server/utils/backend'

export default defineEventHandler(async (event) => {
  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, statusMessage: '인증이 필요합니다.' })
  }

  const formData = await readMultipartFormData(event)
  if (!formData || formData.length === 0) {
    throw createError({ statusCode: 400, statusMessage: '파일이 필요합니다.' })
  }

  const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

  // Rebuild FormData for backend
  const body = new FormData()
  for (const part of formData) {
    if (part.filename) {
      const blob = new Blob([part.data], { type: part.type || 'application/octet-stream' })
      body.append(part.name || 'file', blob, part.filename)
    } else {
      body.append(part.name || 'field', new TextDecoder().decode(part.data))
    }
  }

  const response = await fetch(`${backendUrl}/api/v1/images/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    body,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: '업로드에 실패했습니다.' }))
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText,
      data: error,
    })
  }

  return response.json()
})
