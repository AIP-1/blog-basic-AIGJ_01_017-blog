import { type FormEvent, useState } from 'react'
import { ApiError, api, redirectToLogin } from '../api/client'
import { errorMessage, fieldMessages } from '../api/errors'
import { REASONS, type ReasonCode } from '../app/admin'
import type { MeState } from '../app/useMe'

const NAMES = { POST: '글', COMMENT: '댓글', BLOG: '블로그' } as const

/**
 * 신고 (ADMIN-04). 누르면 그 자리에 사유 고르기가 열린다. 기타는 설명이 필요하다(500자).
 * 비회원이 누르면 로그인으로 갔다가 돌아온다. 같은 대상을 다시 신고하면 "이미 신고한 글입니다"(409).
 */
export default function ReportButton({ targetType, targetId, me, small = false }: {
  targetType: keyof typeof NAMES
  targetId: number
  me: MeState
  small?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState<ReasonCode>('SPAM')
  const [description, setDescription] = useState('')
  const [sending, setSending] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const name = NAMES[targetType]

  function toggle() {
    if (me.status !== 'member') {
      redirectToLogin()
      return
    }
    setOpen(!open)
    setError(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      await api('/api/reports', {
        method: 'POST',
        body: { targetType, targetId, reason, description: description.trim() || undefined },
      })
      setMessage('신고했습니다. 관리자가 확인합니다.')
      setOpen(false)
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'ALREADY_REPORTED') {
        setMessage(`이미 신고한 ${name}입니다.`)
        setOpen(false)
      } else {
        setError(fieldMessages(caught).description ?? errorMessage(caught))
      }
    } finally {
      setSending(false)
    }
  }

  if (message) {
    return <span className="small muted" role="status">{message}</span>
  }
  return (
    <span className="stack" style={{ gap: 6 }}>
      <button className={small ? 'btn ghost small' : 'btn'} type="button" onClick={toggle} aria-expanded={open}>
        신고
      </button>
      {open && (
        <form className="box stack" onSubmit={submit} aria-label={`${name} 신고`} style={{ maxWidth: 420 }}>
          <b className="small">{name} 신고 사유</b>
          <div className="row">
            {REASONS.map((item) => (
              <label key={item.code} className="row small nowrap">
                <input type="radio" name={`report-${targetType}-${targetId}`} checked={reason === item.code}
                       onChange={() => setReason(item.code)} />
                {item.label}
              </label>
            ))}
          </div>
          <textarea value={description} maxLength={500} rows={2} aria-label="신고 설명"
                    placeholder={reason === 'ETC' ? '설명 (기타는 필수, 500자)' : '설명 (선택, 500자)'}
                    onChange={(event) => setDescription(event.target.value)} />
          {error && <p className="err" role="alert">{error}</p>}
          <div className="row">
            <button className="btn primary" type="submit"
                    disabled={sending || (reason === 'ETC' && !description.trim())}>신고</button>
            <button className="btn" type="button" onClick={() => setOpen(false)}>취소</button>
          </div>
        </form>
      )}
    </span>
  )
}
