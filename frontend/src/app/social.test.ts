import { describe, expect, it } from 'vitest'
import { providerName, socialAuthorizeUrl, socialErrorMessage, withObjectParticle } from './social'

describe('socialAuthorizeUrl', () => {
  it('mode와 돌아갈 곳을 쿼리로 붙인다', () => {
    expect(socialAuthorizeUrl('kakao', 'login', 'http://alpha.blog.test/12'))
      .toBe('/api/auth/oauth/kakao/authorize?mode=login&redirect=http%3A%2F%2Falpha.blog.test%2F12')
    expect(socialAuthorizeUrl('kakao', 'link')).toBe('/api/auth/oauth/kakao/authorize?mode=link')
  })
})

describe('socialErrorMessage', () => {
  it('서버가 돌려보낸 error를 문장으로, 없으면 null', () => {
    expect(socialErrorMessage(null)).toBeNull()
    expect(socialErrorMessage('SOCIAL_ALREADY_LINKED')).toBe('이미 다른 계정에 연결된 소셜 계정입니다.')
    expect(socialErrorMessage('SOMETHING_NEW')).toBe('소셜 로그인에 실패했습니다. 다시 시도해 주세요.')
  })
})

describe('providerName', () => {
  it('주소의 provider를 한글 이름으로', () => {
    expect(providerName('kakao')).toBe('카카오')
    expect(providerName('KAKAO')).toBe('카카오')
    expect(providerName(null)).toBe('소셜')
  })
})

describe('withObjectParticle', () => {
  it('받침에 맞춰 을/를', () => {
    expect(withObjectParticle('카카오')).toBe('카카오를')
    expect(withObjectParticle('구글')).toBe('구글을')
  })
})
