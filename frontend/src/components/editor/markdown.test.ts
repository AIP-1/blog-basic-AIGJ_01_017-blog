import { describe, expect, it } from 'vitest'
import { MARKDOWN_LINK, isSafeLinkUrl, looksLikeMarkdown } from './markdown'

describe('isSafeLinkUrl', () => {
  it('http/https 절대 주소만', () => {
    expect(isSafeLinkUrl('https://spring.io/guides')).toBe(true)
    expect(isSafeLinkUrl('http://example.com')).toBe(true)
    expect(isSafeLinkUrl('javascript:alert(1)')).toBe(false)
    expect(isSafeLinkUrl('/relative')).toBe(false)
    expect(isSafeLinkUrl('https://a b')).toBe(false)
  })
})

describe('MARKDOWN_LINK', () => {
  it('닫는 괄호까지 친 링크 문법을 찾는다', () => {
    const match = '자세한 건 [스프링](https://spring.io)'.match(MARKDOWN_LINK)
    expect(match?.[1]).toBe('스프링')
    expect(match?.[2]).toBe('https://spring.io')
  })

  it('괄호를 닫기 전이나 주소에 공백이 있으면 아니다', () => {
    expect('[스프링](https://spring.io'.match(MARKDOWN_LINK)).toBeNull()
    expect('[스프링](https://a b)'.match(MARKDOWN_LINK)).toBeNull()
  })
})

describe('looksLikeMarkdown', () => {
  it('마크다운 모양이 있으면 마크다운으로 본다', () => {
    expect(looksLikeMarkdown('## 제목\n본문')).toBe(true)
    expect(looksLikeMarkdown('- 하나\n- 둘')).toBe(true)
    expect(looksLikeMarkdown('1. 첫째')).toBe(true)
    expect(looksLikeMarkdown('> 인용')).toBe(true)
    expect(looksLikeMarkdown('```java\nint x;\n```')).toBe(true)
    expect(looksLikeMarkdown('이건 **굵게** 쓴 문장')).toBe(true)
    expect(looksLikeMarkdown('[링크](https://spring.io)')).toBe(true)
  })

  it('평범한 문장은 그대로 붙인다', () => {
    expect(looksLikeMarkdown('오늘은 2*3=6을 배웠다.')).toBe(false)
    expect(looksLikeMarkdown('snake_case_name 변수')).toBe(false)
    expect(looksLikeMarkdown('#해시태그 붙인 문장')).toBe(false)
  })
})
