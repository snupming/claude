import { z } from 'zod'
import { zh } from '~/server/utils/validate'

const ConnectBody = z.object({
  platform: z.string().min(1),
})

export default defineEventHandler(async (event) => {
  const body = await readValidatedBody(event, zh(ConnectBody))

  return authedBackendFetch(event, '/api/v1/stores/connect', {
    method: 'POST',
    body,
  })
})
