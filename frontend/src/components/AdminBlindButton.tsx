import { type FormEvent, useState } from 'react'
import { api } from '../api/client'
import { errorMessage, fieldMessages } from '../api/errors'
import type { ReasonCode } from '../app/admin'
import SanctionFields from './SanctionFields'

/**
 * 서비스 관리자의 "숨기기"와 "숨김 해제" (ADMIN-03). 글 상세·댓글에서 관리자에게만 보인다.
 * 권한은 서버가(/api/admin/**은 관리자만) 다시 검사한다. 숨기면 onDone으로 화면을 다시 그린다.
 */
export default function AdminBlindButton({ kind, id, blinded, onDone }: {
  kind: 'posts' | 'comments'
  id: number
  blinded: boolean
  onDone: (blinded: boolean) => void
}) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<ReasonCode>('SPAM')
  const [detail, setDetail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  async function blind(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      await api(`/api/admin/${kind}/${id}/blind`, { method: 'POST', body: { reason, reasonDetail: detail.trim() || undefined } })
      setOpen(false)
      onDone(true)
    } catch (caught) {
      setError(fieldMessages(caught).reasonDetail ?? errorMessage(caught))
    } finally {
      setSending(false)
    }
  }

  async function unblind() {
    setError(null)
    try {
      await api(`/api/admin/${kind}/${id}/blind`, { method: 'DELETE' })
      onDone(false)
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  if (blinded) {
    return (
      <span className="row small">
        <button className="btn ghost small danger" type="button" onClick={unblind}>숨김 해제 (관리자)</button>
        {error && <span className="err">{error}</span>}
      </span>
    )
  }
  return (
    <span className="stack" style={{ gap: 6 }}>
      <button className="btn ghost small danger" type="button" onClick={() => setOpen(!open)} aria-expanded={open}>
        숨기기 (관리자)
      </button>
      {open && (
        <form className="box stack" onSubmit={blind} aria-label="관리자 숨기기" style={{ maxWidth: 520 }}>
          <SanctionFields reason={reason} detail={detail} onReason={setReason} onDetail={setDetail} error={error} />
          <div className="row">
            <button className="btn danger" type="submit" disabled={sending || (reason === 'ETC' && !detail.trim())}>
              숨기기
            </button>
            <button className="btn" type="button" onClick={() => setOpen(false)}>취소</button>
          </div>
        </form>
      )}
    </span>
  )
}
