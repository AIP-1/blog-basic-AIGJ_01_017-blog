// 지금 화면이 플랫폼인지, 어느 블로그(/@{address})인지 판단하고 주소를 만든다 (R-04, 스텝 21)
// 블로그 주소는 서브도메인이 아니라 플랫폼 주소 아래 경로다: blog.com/@alpha/12 (2026-10-12 지원 결정)

export type Host = { kind: 'platform' } | { kind: 'blog'; address: string }

/** /@alpha 또는 /@alpha/… 의 alpha. 주소 규칙(영문 소문자·숫자·하이픈)은 서버가 다시 본다 */
const BLOG_PATH = /^\/@([A-Za-z0-9-]+)(?=\/|$)/

/** 경로(location.pathname)로 플랫폼·블로그를 나눈다 */
export function parseLocation(pathname: string): Host {
  const match = BLOG_PATH.exec(pathname)
  return match ? { kind: 'blog', address: match[1].toLowerCase() } : { kind: 'platform' }
}

/** 지금 화면의 블로그 주소. 플랫폼 화면이면 null. API 클라이언트가 X-Blog-Address로 보낸다 */
export function currentBlogAddress(location: Location = window.location): string | null {
  const host = parseLocation(location.pathname)
  return host.kind === 'blog' ? host.address : null
}

/** 블로그 화면들의 기준 경로(라우터 basename). /@alpha */
export function blogBase(address: string): string {
  return `/@${address}`
}

/** 플랫폼 화면의 전체 주소. 블로그 화면에서 로그인·마이페이지로 보낼 때 쓴다(라우터 밖으로 새로 연다) */
export function platformUrl(path: string, location: Location = window.location): string {
  return `${location.origin}${path}`
}

/** 블로그 화면의 전체 주소. path가 '/'면 블로그 첫 화면(/@alpha) */
export function blogUrl(address: string, path = '/', location: Location = window.location): string {
  return `${location.origin}${blogBase(address)}${path === '/' ? '' : path}`
}

/** 사람에게 보여 줄 블로그 주소: blog.com/@alpha */
export function blogDisplayAddress(address: string, location: Location = window.location): string {
  return `${location.host}${blogBase(address)}`
}

/**
 * 로그인 뒤 돌아갈 주소. 우리 서비스 주소(지금과 같은 출처)만 허용한다(열린 리다이렉트 방지).
 * 블로그도 같은 출처의 /@주소 경로라 출처만 보면 된다. 허용하지 않는 주소면 null이다.
 */
export function safeRedirect(target: string | null, location: Location = window.location): string | null {
  if (!target) {
    return null
  }
  let url: URL
  try {
    url = new URL(target)
  } catch {
    return null
  }
  return (url.protocol === 'http:' || url.protocol === 'https:') && url.origin === location.origin ? url.href : null
}
