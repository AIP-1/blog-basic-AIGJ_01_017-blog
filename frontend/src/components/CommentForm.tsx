import { type FormEvent, useRef, useState } from 'react'
import { api, newIdempotencyKey } from '../api/client'
import { errorMessage, fieldMessages } from '../api/errors'
import type { Comment } from '../api/types'

const MAX_LENGTH = 1000

/**
 * 댓글·방명록·답글 쓰기 칸. path로 보낼 곳을 정한다(/api/posts/{id}/comments 또는 /api/guestbook).
 * 등록 한 번에 연타 방지 키 하나, 실패해 다시 누르면 같은 키, 성공하면 새 키.
 * allowSecret이면 "비밀글" 칸이 보인다(방명록, CMT-04. 댓글의 비밀댓글 CMT-06은 백로그).
 */
export default function CommentForm({ path, parentId, placeholder, allowSecret = false, onCreated, onError }: {
  path: string
  parentId: number | null
  placeholder: string
  allowSecret?: boolean
  onCreated: (comment: Comment) => void
  onError: (message: string | null) => void
}) {
  const [content, setContent] = useState('')
  const [secret, setSecret] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const idempotencyKey = useRef(newIdempotencyKey())
  // 버튼은 다음 그리기에서야 꺼지므로, 그 사이 두 번째 클릭은 ref로 바로 막는다
  const inFlight = useRef(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!content.trim()) {
      onError('내용을 입력해 주세요.')
      return
    }
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    setSubmitting(true)
    onError(null)
    try {
      const body = allowSecret ? { content: content.trim(), parentId, secret } : { content: content.trim(), parentId }
      const created = await api<Comment>(path, { method: 'POST', body, idempotencyKey: idempotencyKey.current })
      idempotencyKey.current = newIdempotencyKey()
      setContent('')
      setSecret(false)
      onCreated(created)
    } catch (caught) {
      onError(fieldMessages(caught).content ?? fieldMessages(caught).parentId ?? errorMessage(caught))
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <textarea value={content} maxLength={MAX_LENGTH} placeholder={placeholder}
                onChange={(event) => setContent(event.target.value)} />
      <div className="row between">
        <span className="small muted num">{content.length}/{MAX_LENGTH}</span>
        <div className="row">
          {allowSecret && (
            <label className="row small">
              <input type="checkbox" checked={secret} onChange={(event) => setSecret(event.target.checked)} />
              비밀글
            </label>
          )}
          <button className="btn primary" type="submit" disabled={submitting || !content.trim()}>등록</button>
        </div>
      </div>
    </form>
  )
}
