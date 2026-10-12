import { describe, expect, it } from 'vitest'
import { blogDisplayAddress, blogUrl, currentBlogAddress, parseLocation, platformUrl, safeRedirect } from './host'

const location = { origin: 'http://blog.test:5173', host: 'blog.test:5173', pathname: '/' } as Location

describe('parseLocation', () => {
  it('/@주소로 시작하면 블로그, 아니면 플랫폼', () => {
    expect(parseLocation('/')).toEqual({ kind: 'platform' })
    expect(parseLocation('/login')).toEqual({ kind: 'platform' })
    expect(parseLocation('/@alpha')).toEqual({ kind: 'blog', address: 'alpha' })
    expect(parseLocation('/@My-Blog/12')).toEqual({ kind: 'blog', address: 'my-blog' })
    expect(parseLocation('/@alpha/manage/posts')).toEqual({ kind: 'blog', address: 'alpha' })
  })

  it('@만 있거나 주소에 쓸 수 없는 글자면 플랫폼으로 그린다', () => {
    expect(parseLocation('/@')).toEqual({ kind: 'platform' })
    expect(parseLocation('/@a.b/1')).toEqual({ kind: 'platform' })
  })

  it('지금 블로그 주소', () => {
    expect(currentBlogAddress({ ...location, pathname: '/@alpha/3' })).toBe('alpha')
    expect(currentBlogAddress(location)).toBeNull()
  })
})

describe('주소 만들기', () => {
  it('지금 출처(포트 포함) 아래 경로', () => {
    expect(platformUrl('/login', location)).toBe('http://blog.test:5173/login')
    expect(blogUrl('alpha', '/12', location)).toBe('http://blog.test:5173/@alpha/12')
    expect(blogUrl('alpha', '/', location)).toBe('http://blog.test:5173/@alpha')
    expect(blogDisplayAddress('alpha', location)).toBe('blog.test:5173/@alpha')
  })
})

describe('safeRedirect', () => {
  it('같은 출처(플랫폼과 블로그 화면)만 돌아간다', () => {
    expect(safeRedirect('http://blog.test:5173/@alpha/manage', location)).toBe('http://blog.test:5173/@alpha/manage')
    expect(safeRedirect('http://blog.test:5173/', location)).toBe('http://blog.test:5173/')
  })

  it('다른 사이트, 다른 포트, 이상한 형식은 막는다', () => {
    expect(safeRedirect('https://evil.com/', location)).toBeNull()
    expect(safeRedirect('http://alpha.blog.test:5173/', location)).toBeNull()
    expect(safeRedirect('http://blog.test:8080/', location)).toBeNull()
    expect(safeRedirect('javascript:alert(1)', location)).toBeNull()
    expect(safeRedirect('/relative', location)).toBeNull()
    expect(safeRedirect(null, location)).toBeNull()
  })
})
