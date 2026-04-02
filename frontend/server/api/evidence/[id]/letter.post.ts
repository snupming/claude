export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const format = getQuery(event).format === 'docx' ? 'docx' : 'pdf'
  const body = await readBody(event)

  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, data: { detail: '인증이 필요합니다.' } })
  }

  const backendUrl = useRuntimeConfig().backendUrl as string
  const response = await fetch(`${backendUrl}/api/v1/evidence/${id}/letter.${format}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ detail: '내용증명 생성 실패' }))
    throw createError({ statusCode: response.status, data: error })
  }

  const contentType = format === 'docx'
    ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    : 'application/pdf'

  setResponseHeader(event, 'Content-Type', contentType)
  setResponseHeader(event, 'Content-Disposition', `attachment; filename=certified_letter_${id}.${format}`)

  const buffer = await response.arrayBuffer()
  return Buffer.from(buffer)
})
