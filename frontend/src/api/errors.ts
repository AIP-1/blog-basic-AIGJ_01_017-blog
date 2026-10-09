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

/**
 * 화면에 띄울 문장. 서버 message는 화면에 그대로 띄워도 되는 문장이다(COM-02).
 * 429(TOO_MANY_REQUESTS)에 기다릴 시간이 있으면 "몇 분 뒤"를 붙인다(비밀번호·인증 코드 5번 틀림 등, R-17).
 */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  }
  const seconds = retryAfterSeconds(error)
  if (error.code === 'TOO_MANY_REQUESTS' && seconds !== null) {
    return `여러 번 시도해 잠시 막혔습니다. ${waitText(seconds)} 뒤에 다시 시도해 주세요.`
  }
  return error.message
}

/** 기다릴 시간: 1분이 안 되면 초, 그 이상은 분(올림). */
export function waitText(seconds: number): string {
  return seconds < 60 ? `${seconds}초` : `${Math.ceil(seconds / 60)}분`
}

/** 429의 남은 초 */
export function retryAfterSeconds(error: unknown): number | null {
  if (error instanceof ApiError && typeof error.detail?.retryAfterSeconds === 'number') {
    return error.detail.retryAfterSeconds
  }
  return null
}
