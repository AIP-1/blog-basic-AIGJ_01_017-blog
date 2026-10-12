import { describe, expect, it } from 'vitest'
import { barHeights, bucketLabel, referrerLabel } from './stats'

describe('통계 그래프', () => {
  it('칸 이름', () => {
    expect(bucketLabel('2026-10-07', 'day')).toBe('10/7')
    expect(bucketLabel('2026-10-05', 'week')).toBe('10/5')
    expect(bucketLabel('2026-10-01', 'month')).toBe('10월')
  })

  it('가장 큰 값이 100, 작은 값도 보이게 최소 2, 0은 0', () => {
    const points = [0, 1, 50, 100].map((visitorCount, i) => ({ date: `2026-10-0${i + 1}`, visitorCount, viewCount: 0 }))
    expect(barHeights(points)).toEqual([0, 2, 50, 100])
    expect(barHeights([{ date: '2026-10-01', visitorCount: 0, viewCount: 0 }])).toEqual([0])
  })

  it('유입 종류 이름', () => {
    expect(referrerLabel('INTERNAL')).toBe('블로그 안')
  })
})
