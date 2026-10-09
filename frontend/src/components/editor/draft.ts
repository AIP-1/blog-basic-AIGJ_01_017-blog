// 임시저장 (POST-08): 언제 자동 저장할지 정한다. 화면 없이 테스트할 수 있게 판단만 모았다

/** 자동 저장 간격 1분 (plan.md "자동 임시저장 1분 간격"). */
export const AUTO_SAVE_MS = 60_000

/** 글자도 이미지도 없는 본문인가. 에디터는 빈 본문을 <p></p>로 준다. */
export function isEmptyBody(html: string): boolean {
  return !/<img\s/i.test(html) && html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim() === ''
}

export interface AutoSaveState {
  /** 이 글의 상태. NEW는 아직 한 번도 저장하지 않은 새 글, LOADING은 고칠 글을 불러오는 중 */
  status: 'NEW' | 'LOADING' | 'DRAFT' | 'PUBLISHED' | 'SCHEDULED'
  /** 저장 요청이 나가 있는가. 겹쳐 보내지 않는다 */
  busy: boolean
  /** 관리자가 숨긴 글이면 저장할 수 없다 */
  blinded: boolean
  title: string
  html: string
  /** 지금 입력값을 한 줄로 만든 것과 마지막으로 저장한 것. 같으면 바뀐 것이 없다 */
  snapshot: string
  lastSaved: string | null
}

/**
 * 자동 저장할까. 새 글과 (불러오기를 마친) 임시저장 글만 저장한다(발행한 글을 고치는 중에는 자동 저장하지 않는다. 발행 글은 임시저장으로 못 돌린다).
 * 바뀐 것이 없거나, 아무것도 안 썼거나, 앞 저장이 아직 끝나지 않았으면 건너뛴다.
 */
export function autoSaveNeeded(state: AutoSaveState): boolean {
  if (state.status !== 'NEW' && state.status !== 'DRAFT') {
    return false
  }
  if (state.busy || state.blinded || state.snapshot === state.lastSaved) {
    return false
  }
  return state.status === 'DRAFT' || state.title.trim() !== '' || !isEmptyBody(state.html)
}
