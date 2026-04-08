import type { ZodSchema } from 'zod'

/**
 * zod 스키마를 h3 validator 함수로 변환하는 어댑터.
 *
 * h3 빌트인 `readValidatedBody` / `getValidatedQuery` / `getValidatedRouterParams` 와
 * 함께 사용한다.
 *
 * @example
 * const LoginSchema = z.object({ email: z.string().email(), password: z.string().min(1) })
 * const body = await readValidatedBody(event, zh(LoginSchema))
 */
export function zh<T>(schema: ZodSchema<T>) {
  return (data: unknown): T => {
    const result = schema.safeParse(data)
    if (!result.success) {
      throw createError({
        statusCode: 400,
        statusMessage: 'Invalid request',
        data: result.error.issues,
      })
    }
    return result.data
  }
}
