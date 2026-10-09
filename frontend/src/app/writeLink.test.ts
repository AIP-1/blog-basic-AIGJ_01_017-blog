import { describe, expect, it } from 'vitest'
import { writeUrl } from './writeLink'

describe('writeUrl (AUTH-04)', () => {
  const location = { protocol: 'http:', port: '8080' } as Location

  it('대표 블로그가 있으면 그 블로그의 글쓰기로 간다', () => {
    const me = { primaryBlog: { id: 1, address: 'alpha', name: '알파' } }
    expect(writeUrl(me, location)).toBe('http://alpha.blog.test:8080/manage/write')
  })

  it('블로그가 없으면 글쓰기에서 왔다는 표시와 함께 블로그 개설로 간다', () => {
    expect(writeUrl({ primaryBlog: null }, location)).toBe('http://blog.test:8080/blogs/new?from=write')
  })
})
