import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../../api/client'
import { errorMessage, fieldMessages } from '../../api/errors'
import type { BannedWord, BlockedMember, PageResponse } from '../../api/types'
import { formatDate } from '../../app/format'

/**
 * 스팸·차단 (MNG-04, 목업 manage-spam). 차단한 회원(해제)과 금칙어(100개까지, 더하기·지우기).
 * 차단은 댓글·방명록 관리에서 작성자를 골라 한다. 차단 사실은 상대에게 따로 알리지 않는다.
 */
export default function ManageSpamPage() {
  const [blocked, setBlocked] = useState<PageResponse<BlockedMember> | null>(null)
  const [words, setWords] = useState<BannedWord[]>([])
  const [word, setWord] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [wordError, setWordError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<PageResponse<BlockedMember>>('/api/manage/blocked-members').then(setBlocked).catch((caught: unknown) => setError(errorMessage(caught)))
    api<BannedWord[]>('/api/manage/banned-words').then(setWords).catch((caught: unknown) => setError(errorMessage(caught)))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function unblock(memberId: number) {
    try {
      await api(`/api/manage/blocked-members/${memberId}`, { method: 'DELETE' })
      load()
    } catch (caught) {
      setError(errorMessage(caught))
    }
  }

  async function addWord(event: FormEvent) {
    event.preventDefault()
    setWordError(null)
    try {
      const added = await api<BannedWord>('/api/manage/banned-words', { method: 'POST', body: { word: word.trim() } })
      setWords((previous) => [...previous, added])
      setWord('')
    } catch (caught) {
      setWordError(wordErrorMessage(caught))
    }
  }

  async function removeWord(id: number) {
    try {
      await api(`/api/manage/banned-words/${id}`, { method: 'DELETE' })
      setWords((previous) => previous.filter((item) => item.id !== id))
    } catch (caught) {
      setWordError(errorMessage(caught))
    }
  }

  return (
    <main className="page">
      <h1 style={{ fontSize: 20 }}>스팸·차단</h1>
      {error && <p className="err" role="alert">{error}</p>}
      <section className="section" aria-label="차단한 회원" style={{ maxWidth: 640 }}>
        <h2>차단한 회원</h2>
        {blocked && blocked.content.length === 0 && <p className="small muted">차단한 회원이 없습니다.</p>}
        {blocked && blocked.content.length > 0 && (
          <div className="table-wrap">
            <table className="manage-table">
              <thead><tr><th>회원</th><th>메모</th><th>차단일</th><th /></tr></thead>
              <tbody>
                {blocked.content.map((row) => (
                  <tr key={row.member.id}>
                    <td>{row.member.nickname}</td>
                    <td className="small">{row.memo ?? ''}</td>
                    <td className="num small">{formatDate(row.blockedAt)}</td>
                    <td><button className="btn small" type="button" onClick={() => unblock(row.member.id)}>해제</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="small muted" style={{ margin: 0 }}>차단은 댓글·방명록 관리에서 작성자 옆 "차단"으로 합니다. 차단한 회원은 이 블로그에 댓글·방명록을 쓰거나 구독할 수 없습니다.</p>
      </section>
      <section className="section" aria-label="금칙어" style={{ maxWidth: 640 }}>
        <h2>금칙어 <span className="small muted num">{words.length} / 100</span></h2>
        <div className="tag-cloud">
          {words.map((item) => (
            <span key={item.id} className="chip">
              {item.word}
              <button className="btn ghost small" type="button" aria-label={`${item.word} 지우기`} onClick={() => removeWord(item.id)}>×</button>
            </span>
          ))}
        </div>
        <form className="row" onSubmit={addWord}>
          <input type="text" value={word} maxLength={30} placeholder="금칙어 (1~30자)" aria-label="새 금칙어" style={{ maxWidth: 240 }}
                 onChange={(event) => setWord(event.target.value)} />
          <button className="btn" type="submit" disabled={!word.trim()}>추가</button>
        </form>
        {wordError && <p className="err" role="alert">{wordError}</p>}
        <p className="small muted" style={{ margin: 0 }}>새로 쓰는 댓글·방명록에만, 대소문자를 무시하고 검사합니다. 이미 있던 댓글은 그대로입니다.</p>
      </section>
    </main>
  )
}

function wordErrorMessage(caught: unknown): string {
  if (caught instanceof ApiError && caught.code === 'NAME_TAKEN') {
    return '이미 있는 금칙어입니다.'
  }
  if (caught instanceof ApiError && caught.code === 'BANNED_WORD_LIMIT') {
    return '금칙어는 100개까지 등록할 수 있습니다.'
  }
  return fieldMessages(caught).word ?? errorMessage(caught)
}
