export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params = new URLSearchParams()

  if (query.page) params.set('page', String(query.page))
  if (query.size) params.set('size', String(query.size))
  if (query.status) params.set('status', String(query.status))
  if (query.sourceType) params.set('sourceType', String(query.sourceType))

  const qs = params.toString()
  const path = `/api/v1/images${qs ? `?${qs}` : ''}`

  return authedBackendFetch(event, path)
})
