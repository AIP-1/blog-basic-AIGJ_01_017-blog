import { describe, expect, it } from 'vitest'
import { searchMode, searchQuery, similarityText } from './searchMode'

describe('searchMode', () => {
  it('semantic만 의미로 찾기, 나머지는 글자로 찾기', () => {
    expect(searchMode('semantic')).toBe('semantic')
    expect(searchMode(null)).toBe('keyword')
    expect(searchMode('vector')).toBe('keyword')
  })
})

describe('searchQuery', () => {
  it('의미로 찾기만 mode를 붙인다', () => {
    expect(searchQuery({ q: '자바', page: '2' }, 'semantic').toString()).toBe('q=%EC%9E%90%EB%B0%94&page=2&mode=semantic')
    expect(searchQuery({ q: 'a' }, 'keyword').toString()).toBe('q=a')
  })
})

describe('similarityText', () => {
  it('소수 둘째 자리까지', () => {
    expect(similarityText(0.8)).toBe('0.80')
    expect(similarityText(1)).toBe('1.00')
  })
})
