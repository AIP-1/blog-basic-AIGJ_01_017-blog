import { describe, expect, it } from 'vitest'
import { notificationLabel } from './notifications'

describe('notificationLabel', () => {
  it('읽지 않은 알림이 없으면 글자만, 있으면 수, 100개부터는 99+', () => {
    expect(notificationLabel(0)).toBe('알림')
    expect(notificationLabel(3)).toBe('알림 3')
    expect(notificationLabel(100)).toBe('알림 99+')
  })
})
