/** 머리글의 알림 버튼 글자. 읽지 않은 알림이 있으면 수를 붙이고, 100개부터는 99+ (SUB-04) */
export function notificationLabel(unread: number): string {
  return unread > 0 ? `알림 ${unread > 99 ? '99+' : unread}` : '알림'
}
