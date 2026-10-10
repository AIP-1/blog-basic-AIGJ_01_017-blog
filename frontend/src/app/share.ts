// 글 공유 (SOC-02). 공유하는 주소는 바뀌지 않는 글 주소({address}.blog.com/{글 번호})다(spec US6 시나리오 4).

/** SNS 공유 창 주소. 공유 API를 부르지 않고 각 서비스의 공유 주소를 연다(미리보기는 서버의 Open Graph, T072). */
export function shareLinks(url: string, title: string): { name: string; href: string }[] {
  const u = encodeURIComponent(url)
  const t = encodeURIComponent(title)
  return [
    { name: 'X', href: `https://twitter.com/intent/tweet?url=${u}&text=${t}` },
    { name: '페이스북', href: `https://www.facebook.com/sharer/sharer.php?u=${u}` },
  ]
}

/**
 * 주소 복사. navigator.clipboard는 보안 컨텍스트(HTTPS, localhost)에서만 있어서, 개발 주소(http://alpha.blog.test)에서는
 * 숨긴 입력칸을 골라 execCommand('copy')로 복사한다(연타 방지 키의 crypto.randomUUID와 같은 사정).
 */
export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 권한 거부 등은 아래 방법으로 한 번 더
    }
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  try {
    return document.execCommand('copy')
  } finally {
    document.body.removeChild(area)
  }
}
