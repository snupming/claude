import { z } from 'zod'
import { zh } from '~/server/utils/validate'

const RouteParams = z.object({ id: z.coerce.number().int().positive() })
const FormatQuery = z.object({ format: z.enum(['pdf', 'docx']).default('pdf') })

export default defineEventHandler(async (event) => {
  const { id } = await getValidatedRouterParams(event, zh(RouteParams))
  const { format } = await getValidatedQuery(event, zh(FormatQuery))

  const accessToken = getAccessTokenFromCookie(event)
  if (!accessToken) {
    throw createError({ statusCode: 401, data: { detail: '인증이 필요합니다.' } })
  }

  const backendUrl = useRuntimeConfig().backendUrl as string
  const response = await fetch(`${backendUrl}/api/v1/evidence/${encodeURIComponent(id)}/report.${format}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })

  if (!response.ok) {
    throw createError({ statusCode: response.status, data: { detail: '증거자료 생성 실패' } })
  }

  const contentType = format === 'docx'
    ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    : 'application/pdf'

  setResponseHeader(event, 'Content-Type', contentType)
  setResponseHeader(event, 'Content-Disposition', `attachment; filename=evidence_report_${id}.${format}`)

  const buffer = await response.arrayBuffer()
  return Buffer.from(buffer)
})
