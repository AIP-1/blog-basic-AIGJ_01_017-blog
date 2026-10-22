/** 머리글의 알림 버튼 글자. 읽지 않은 알림이 있으면 수를 붙이고, 100개부터는 99+ (SUB-04) */
export function notificationLabel(unread: number): string {
  if (unread <= 0) {
    return '알림'
  }
  return `알림 ${unread > 99 ? '99+' : unread}`
}
