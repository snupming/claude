import { z } from 'zod'
import { zh } from '~/server/utils/validate'

const RouteParams = z.object({ id: z.coerce.number().int().positive() })
const FormatQuery = z.object({ format: z.enum(['pdf', 'docx']).default('pdf') })

export default defineEventHandler(async (event) => {
  const { id } = await getValidatedRouterParams(event, zh(RouteParams))
  const { format } = await getValidatedQuery(event, zh(FormatQuery))
  const body = await readBody(event)

  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, data: { detail: '인증이 필요합니다.' } })
  }

  const backendUrl = useRuntimeConfig().backendUrl as string
  const response = await fetch(`${backendUrl}/api/v1/evidence/${encodeURIComponent(id)}/letter.${format}`, {
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
