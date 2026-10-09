import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { errorMessage, waitText } from './errors'

describe('errorMessage', () => {
  it('429에 기다릴 시간이 있으면 몇 분 뒤인지 붙인다', () => {
    const error = new ApiError(429, {
      code: 'TOO_MANY_REQUESTS', message: '잠시 후 다시 시도해 주세요.', detail: { retryAfterSeconds: 899 },
    })
    expect(errorMessage(error)).toBe('여러 번 시도해 잠시 막혔습니다. 15분 뒤에 다시 시도해 주세요.')
  })

  it('다른 오류는 서버 문장 그대로', () => {
    const error = new ApiError(401, { code: 'LOGIN_FAILED', message: '이메일 또는 비밀번호가 맞지 않습니다.' })
    expect(errorMessage(error)).toBe('이메일 또는 비밀번호가 맞지 않습니다.')
  })
})

describe('waitText', () => {
  it('1분 미만은 초, 그 이상은 분으로 올림', () => {
    expect(waitText(1)).toBe('1초')
    expect(waitText(59)).toBe('59초')
    expect(waitText(60)).toBe('1분')
    expect(waitText(61)).toBe('2분')
  })
})
