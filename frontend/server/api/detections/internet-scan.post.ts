export default defineEventHandler(async (event) => {
  return authedBackendFetch(event, '/api/v1/detections/internet-scan', { method: 'POST' })
})
