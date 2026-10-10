import { REASONS, type ReasonCode } from '../app/admin'

/**
 * 제재 사유 고르기 (ADMIN-02·03·05). 관리자는 사유를 직접 쓰지 않고 목록에서 고르고, 기타만 설명을 쓴다(200자).
 * 값은 부모가 들고 있다(Thymeleaf 폼의 th:field처럼 값과 바꾸는 함수를 받는다).
 */
export default function SanctionFields({ reason, detail, onReason, onDetail, error }: {
  reason: ReasonCode
  detail: string
  onReason: (reason: ReasonCode) => void
  onDetail: (detail: string) => void
  error?: string | null
}) {
  return (
    <div className="stack" style={{ gap: 6 }}>
      <div className="row" role="radiogroup" aria-label="사유">
        {REASONS.map((item) => (
          <label key={item.code} className="row small nowrap">
            <input type="radio" name="sanction-reason" checked={reason === item.code}
                   onChange={() => onReason(item.code)} />
            {item.label}
          </label>
        ))}
      </div>
      {reason === 'ETC' && (
        <input type="text" value={detail} maxLength={200} placeholder="사유 설명 (필수, 200자)" aria-label="사유 설명"
               onChange={(event) => onDetail(event.target.value)} />
      )}
      {error && <p className="err" role="alert">{error}</p>}
    </div>
  )
}
