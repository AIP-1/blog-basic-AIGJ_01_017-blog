import { ApiError } from './client'

/** 입력 칸 아래에 띄울 오류. 칸 이름(field)별 문장이다. */
export type FieldMessages = Record<string, string>

/** 400 VALIDATION_FAILED 등의 fieldErrors를 칸별 문장으로. */
export function fieldMessages(error: unknown): FieldMessages {
  if (!(error instanceof ApiError)) {
    return {}
  }
  return Object.fromEntries(error.fieldErrors.map((fieldError) => [fieldError.field, fieldError.reason]))
}

/** 화면에 띄울 문장. 서버 message는 화면에 그대로 띄워도 되는 문장이다(COM-02). */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
}

/** 429의 남은 초 */
export function retryAfterSeconds(error: unknown): number | null {
  if (error instanceof ApiError && typeof error.detail?.retryAfterSeconds === 'number') {
    return error.detail.retryAfterSeconds
  }
  return null
}
