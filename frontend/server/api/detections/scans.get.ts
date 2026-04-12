import { z } from 'zod'
import { zh } from '~/server/utils/validate'

const PageQuery = z.object({
  page: z.coerce.number().int().nonnegative().optional(),
  size: z.coerce.number().int().positive().max(200).optional(),
})

export default defineEventHandler(async (event) => {
  const query = await getValidatedQuery(event, zh(PageQuery))
  const params = new URLSearchParams()

  if (query.page !== undefined) params.set('page', String(query.page))
  if (query.size !== undefined) params.set('size', String(query.size))

  const qs = params.toString()
  const path = `/api/v1/detections/scans${qs ? `?${qs}` : ''}`

  return authedBackendFetch(event, path)
})
