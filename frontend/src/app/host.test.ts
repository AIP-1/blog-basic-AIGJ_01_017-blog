import { describe, expect, it } from 'vitest'
import { blogUrl, parseHost, platformUrl, safeRedirect } from './host'

describe('parseHost', () => {
  it('플랫폼 주소', () => {
    expect(parseHost('blog.test', 'blog.test')).toEqual({ kind: 'platform' })
    expect(parseHost('www.blog.test', 'blog.test')).toEqual({ kind: 'platform' })
    expect(parseHost('localhost', 'blog.test')).toEqual({ kind: 'platform' })
  })

  it('블로그 주소', () => {
    expect(parseHost('alpha.blog.test', 'blog.test')).toEqual({ kind: 'blog', address: 'alpha' })
    expect(parseHost('My-Blog.blog.com', 'blog.com')).toEqual({ kind: 'blog', address: 'my-blog' })
  })

  it('하위 단계가 둘이거나 다른 도메인이면 플랫폼으로 그린다', () => {
    expect(parseHost('a.b.blog.test', 'blog.test')).toEqual({ kind: 'platform' })
    expect(parseHost('evilblog.test', 'blog.test')).toEqual({ kind: 'platform' })
  })
})

describe('주소 만들기', () => {
  const location = { protocol: 'http:', port: '5173' } as Location

  it('포트를 유지한다', () => {
    expect(platformUrl('/login', location)).toBe('http://blog.test:5173/login')
    expect(blogUrl('alpha', '/12', location)).toBe('http://alpha.blog.test:5173/12')
  })
})

describe('safeRedirect', () => {
  it('플랫폼과 블로그 주소만 돌아간다', () => {
    expect(safeRedirect('http://alpha.blog.test:5173/manage', 'blog.test')).toBe('http://alpha.blog.test:5173/manage')
    expect(safeRedirect('http://blog.test:8080/', 'blog.test')).toBe('http://blog.test:8080/')
  })

  it('다른 사이트, 이상한 형식은 막는다', () => {
    expect(safeRedirect('https://evil.com/', 'blog.test')).toBeNull()
    expect(safeRedirect('https://evilblog.test/', 'blog.test')).toBeNull()
    expect(safeRedirect('javascript:alert(1)', 'blog.test')).toBeNull()
    expect(safeRedirect('/relative', 'blog.test')).toBeNull()
    expect(safeRedirect(null, 'blog.test')).toBeNull()
  })
})
