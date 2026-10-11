import type { ReferrerType, StatsPoint } from '../api/types'

export type StatsUnit = 'day' | 'week' | 'month'

/** 그래프 칸 이름. 일·주는 "10/7"(주는 그 주 월요일), 월은 "10월" */
export function bucketLabel(date: string, unit: StatsUnit): string {
  const [, month, day] = date.split('-').map(Number)
  return unit === 'month' ? `${month}월` : `${month}/${day}`
}

/** 막대 높이(%). 가장 큰 값이 100, 값이 있으면 최소 2로 보이게 */
export function barHeights(points: StatsPoint[]): number[] {
  const max = Math.max(0, ...points.map((point) => point.visitorCount))
  return points.map((point) => {
    if (max === 0 || point.visitorCount === 0) {
      return 0
    }
    return Math.max(2, Math.round((point.visitorCount / max) * 100))
  })
}

const REFERRER_LABELS: Record<ReferrerType, string> = {
  SEARCH: '검색', SNS: 'SNS', DIRECT: '직접', INTERNAL: '블로그 안', OTHER: '기타',
}

export function referrerLabel(type: ReferrerType): string {
  return REFERRER_LABELS[type]
}
