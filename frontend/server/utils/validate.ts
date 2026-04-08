import type { H3Event } from 'h3'
import type { ZodSchema } from 'zod'

/**
 * server/api 라우트의 body / query 검증 헬퍼.
 * 모든 server/api/* 핸들러는 외부 입력을 zod 스키마로 검증해야 한다.
 */

function format(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Invalid request'
}

export async function readValidatedBody<T>(
  event: H3Event,
  schema: ZodSchema<T>,
): Promise<T> {
  const body = await readBody(event)
  const result = schema.safeParse(body)
  if (!result.success) {
    throw createError({
      statusCode: 400,
      statusMessage: format(result.error),
      data: result.error.issues,
    })
  }
  return result.data
}

export function getValidatedQuery<T>(
  event: H3Event,
  schema: ZodSchema<T>,
): T {
  const result = schema.safeParse(getQuery(event))
  if (!result.success) {
    throw createError({
      statusCode: 400,
      statusMessage: format(result.error),
      data: result.error.issues,
    })
  }
  return result.data
}

export function getValidatedRouterParams<T>(
  event: H3Event,
  schema: ZodSchema<T>,
): T {
  const result = schema.safeParse(getRouterParams(event))
  if (!result.success) {
    throw createError({
      statusCode: 400,
      statusMessage: format(result.error),
      data: result.error.issues,
    })
  }
  return result.data
}
