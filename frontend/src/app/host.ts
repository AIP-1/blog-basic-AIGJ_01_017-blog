// 지금 주소가 플랫폼(blog.com)인지, 어느 블로그({address}.blog.com)인지 판단한다 (R-04)

export const PLATFORM_DOMAIN: string = import.meta.env.VITE_PLATFORM_DOMAIN ?? 'blog.test'

export type Host = { kind: 'platform' } | { kind: 'blog'; address: string }

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1'])

export function parseHost(hostname: string, platform: string = PLATFORM_DOMAIN): Host {
  const host = hostname.toLowerCase()
  if (host === platform || host === `www.${platform}` || LOCAL_HOSTS.has(host)) {
    return { kind: 'platform' }
  }
  const suffix = `.${platform}`
  if (host.endsWith(suffix)) {
    const address = host.slice(0, -suffix.length)
    if (address && !address.includes('.')) {
      return { kind: 'blog', address }
    }
  }
  // 그 밖의 주소는 플랫폼으로 그린다. 없는 블로그는 서버가 먼저 404로 막는다
  return { kind: 'platform' }
}

/** 플랫폼 주소의 경로. 블로그 주소에서 로그인 화면으로 보낼 때 쓴다. */
export function platformUrl(path: string, location: Location = window.location): string {
  const port = location.port ? `:${location.port}` : ''
  return `${location.protocol}//${PLATFORM_DOMAIN}${port}${path}`
}

export function blogUrl(address: string, path = '/', location: Location = window.location): string {
  const port = location.port ? `:${location.port}` : ''
  return `${location.protocol}//${address}.${PLATFORM_DOMAIN}${port}${path}`
}

/**
 * 로그인 뒤 돌아갈 주소. 우리 서비스 주소(플랫폼, 블로그 주소)만 허용한다(열린 리다이렉트 방지).
 * 허용하지 않는 주소면 null이다.
 */
export function safeRedirect(target: string | null, platform: string = PLATFORM_DOMAIN): string | null {
  if (!target) {
    return null
  }
  let url: URL
  try {
    url = new URL(target)
  } catch {
    return null
  }
  const host = url.hostname.toLowerCase()
  const ours = host === platform || host.endsWith(`.${platform}`)
  return (url.protocol === 'http:' || url.protocol === 'https:') && ours ? url.href : null
}
