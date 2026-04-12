export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')

  return authedBackendFetch(event, `/api/v1/images/${encodeURIComponent(id ?? '')}`)
})
