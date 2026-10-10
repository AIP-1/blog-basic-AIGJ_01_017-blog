import { describe, expect, it } from 'vitest'
import { shouldRedirectToLogin } from './useMe'

describe('shouldRedirectToLogin', () => {
  it('비회원이면 로그인 화면으로 보낸다', () => {
    expect(shouldRedirectToLogin({ status: 'anonymous' })).toBe(true)
  })

  it('로그인한 채로 정지된 회원이면 보내지 않고 정지 안내를 띄운다', () => {
    const suspension = { reason: 'SPAM', reasonMessage: '스팸', suspendedUntil: null }
    expect(shouldRedirectToLogin({ status: 'anonymous', suspension })).toBe(false)
  })

  it('불러오는 중이거나 회원이면 보내지 않는다', () => {
    expect(shouldRedirectToLogin({ status: 'loading' })).toBe(false)
  })
})
