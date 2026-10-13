/**
 * 소셜 로그인 (T099, T100). 버튼은 서버의 authorize 주소로 화면을 옮긴다(fetch가 아니라 이동).
 * 서버가 제공사 로그인 화면으로 302하고, 끝나면 서버가 갈 곳으로 돌려보낸다(contracts 소셜 로그인 흐름).
 */

export type SocialPath = 'kakao' | 'google'
export type SocialMode = 'login' | 'link' | 'reauth'

export interface SocialProviderInfo {
  path: SocialPath
  /** Me.socialAccounts[].provider 값 */
  key: 'KAKAO' | 'GOOGLE'
  name: string
}

export const SOCIAL_PROVIDERS: SocialProviderInfo[] = [
  { path: 'kakao', key: 'KAKAO', name: '카카오' },
  { path: 'google', key: 'GOOGLE', name: '구글' },
]

export function providerName(value: string | null): string {
  const lower = value?.toLowerCase()
  return SOCIAL_PROVIDERS.find((provider) => provider.path === lower)?.name ?? '소셜'
}

/** 플랫폼 주소의 시작 주소. redirect는 로그인 뒤 돌아갈 곳(서버가 우리 주소만 받는다). */
export function socialAuthorizeUrl(provider: SocialPath, mode: SocialMode, redirect?: string | null): string {
  const params = new URLSearchParams({ mode })
  if (redirect) {
    params.set('redirect', redirect)
  }
  return `/api/auth/oauth/${provider}/authorize?${params.toString()}`
}

/** 서버가 돌려보낸 주소의 error 값 → 화면 문장. 모르는 값이면 null. */
export function socialErrorMessage(error: string | null): string | null {
  switch (error) {
    case null:
      return null
    case 'OAUTH_CANCELLED':
      return '소셜 로그인을 취소했습니다.'
    case 'OAUTH_FAILED':
      return '소셜 로그인에 실패했습니다. 다시 시도해 주세요.'
    case 'OAUTH_UNAVAILABLE':
      return '지금은 이 소셜 로그인을 쓸 수 없습니다.'
    case 'MEMBER_SUSPENDED':
      return '이용이 정지된 계정입니다.'
    case 'SOCIAL_ALREADY_LINKED':
      return '이미 다른 계정에 연결된 소셜 계정입니다.'
    case 'PROVIDER_ALREADY_LINKED':
      return '같은 서비스의 계정이 이미 하나 연결되어 있습니다.'
    case 'REAUTH_FAILED':
      return '이 계정에 연결된 소셜 계정으로 확인해 주세요.'
    default:
      return '소셜 로그인에 실패했습니다. 다시 시도해 주세요.'
  }
}

/** 이름 뒤 목적격 조사: 받침이 있으면 '을', 없으면 '를' (카카오를, 구글을). */
export function withObjectParticle(name: string): string {
  const last = name.charCodeAt(name.length - 1)
  const hangul = last >= 0xac00 && last <= 0xd7a3
  return name + (hangul && (last - 0xac00) % 28 !== 0 ? '을' : '를')
}
