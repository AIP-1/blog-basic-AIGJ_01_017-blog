/** "2026-10-08T09:00:00+09:00" → "2026.10.08". 서버 시각은 한국 시간이라 앞 10자가 그날 날짜다. */
export function formatDate(iso: string | null): string {
  return iso ? iso.slice(0, 10).replaceAll('-', '.') : ''
}

/** "2026-11-07T13:00:00+09:00" → "2026.11.07 13:00" */
export function formatDateTime(iso: string): string {
  return `${formatDate(iso)} ${iso.slice(11, 16)}`
}
