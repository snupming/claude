export default defineEventHandler(async (event) => {
  const body = await readBody(event)

  return authedBackendFetch(event, '/api/v1/stores/connect', {
    method: 'POST',
    body,
  })
})
