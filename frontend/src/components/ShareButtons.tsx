import { useState } from 'react'
import { copyText, shareLinks } from '../app/share'

/**
 * 글 상세의 공유 (SOC-02, 목업 post-detail "주소 복사·공유"). 공유 API는 없다.
 * 주소는 지금 블로그 주소의 /{글 번호}로, 글 제목이 바뀌어도 그대로다.
 */
export default function ShareButtons({ postId, title }: { postId: number; title: string }) {
  const [message, setMessage] = useState<string | null>(null)
  const url = `${window.location.origin}/${postId}`

  async function copy() {
    setMessage(await copyText(url) ? '주소를 복사했습니다.' : `복사하지 못했습니다. ${url}`)
  }

  return (
    <span className="row small">
      <button className="btn" type="button" onClick={copy}>주소 복사</button>
      {shareLinks(url, title).map((link) => (
        <a key={link.name} className="btn" href={link.href} target="_blank" rel="noopener noreferrer">{link.name}</a>
      ))}
      {message && <span className="muted" role="status">{message}</span>}
    </span>
  )
}
