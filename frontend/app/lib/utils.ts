import type { ClassValue } from "clsx"
import { clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * $fetch / ofetch 에러 객체에서 사용자에게 보여줄 메시지를 추출한다.
 * 구조: { data: { detail?, statusMessage? } } 또는 Error.message
 */
export function extractErrorMessage(err: unknown): string | undefined {
  if (typeof err === 'object' && err !== null) {
    const withData = err as { data?: { detail?: unknown, statusMessage?: unknown } }
    const detail = withData.data?.detail
    if (typeof detail === 'string') return detail
    const statusMessage = withData.data?.statusMessage
    if (typeof statusMessage === 'string') return statusMessage
  }
  if (err instanceof Error) return err.message
  return undefined
}
