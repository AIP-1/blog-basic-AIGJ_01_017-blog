import { describe, expect, it } from 'vitest'
import { bodyImageUrls, effectiveThumbnail, thumbnailChoices } from './thumbnail'

const a = { id: 1, url: '/uploads/a.png', thumbnailUrl: '/uploads/t_a.png' }
const b = { id: 2, url: '/uploads/b.jpg', thumbnailUrl: '/uploads/t_b.jpg' }

describe('대표 이미지 후보 (POST-07)', () => {
  it('본문 순서대로, 같은 이미지는 한 번', () => {
    const html = '<p><img src="/uploads/b.jpg" alt="b"></p><img alt="a" src="/uploads/a.png"><img src="/uploads/b.jpg">'
    expect(bodyImageUrls(html)).toEqual(['/uploads/b.jpg', '/uploads/a.png'])
  })

  it('번호를 아는 이미지만 후보다', () => {
    const html = '<img src="/uploads/a.png"><img src="/uploads/x.png">'
    expect(thumbnailChoices(html, { [a.url]: a, [b.url]: b })).toEqual([a])
  })

  it('고른 이미지를 본문에서 지우면 고르지 않은 것(null)이 된다', () => {
    expect(effectiveThumbnail(2, [a, b])).toBe(2)
    expect(effectiveThumbnail(2, [a])).toBeNull()
    expect(effectiveThumbnail(null, [a])).toBeNull()
  })
})
