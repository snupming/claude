import { z } from 'zod'
import { zh } from '~/server/utils/validate'

const ListQuery = z.object({
  page: z.coerce.number().int().nonnegative().optional(),
  size: z.coerce.number().int().positive().max(200).optional(),
  status: z.string().optional(),
  sourceType: z.string().optional(),
})

export default defineEventHandler(async (event) => {
  const query = await getValidatedQuery(event, zh(ListQuery))
  const params = new URLSearchParams()

  if (query.page !== undefined) params.set('page', String(query.page))
  if (query.size !== undefined) params.set('size', String(query.size))
  if (query.status) params.set('status', query.status)
  if (query.sourceType) params.set('sourceType', query.sourceType)

  const qs = params.toString()
  const path = `/api/v1/images${qs ? `?${qs}` : ''}`

  return authedBackendFetch(event, path)
})
