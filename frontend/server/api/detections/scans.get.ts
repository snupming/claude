export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params = new URLSearchParams()

  if (query.page) params.set('page', String(query.page))
  if (query.size) params.set('size', String(query.size))

  const qs = params.toString()
  const path = `/api/v1/detections/scans${qs ? `?${qs}` : ''}`

  return authedBackendFetch(event, path)
})
