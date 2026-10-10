import { useEffect, useState } from 'react'
import { ApiError, api, suspensionDetail } from '../api/client'
import type { Me, SuspensionDetail } from '../api/types'

/**
 * 로그인 상태. suspension은 이미 로그인한 회원이 정지된 경우의 사유·기한이다(서버가 쿠키를 지웠으니 이제 비회원).
 */
export type MeState =
  | { status: 'loading' }
  | { status: 'anonymous'; suspension?: SuspensionDetail }
  | { status: 'member'; me: Me }

/**
 * 로그인 상태. GET /api/me가 401이면 비회원이다.
 * 로그인 쿠키는 HttpOnly라 자바스크립트가 직접 읽을 수 없어서 서버에 물어본다.
 * 403 MEMBER_SUSPENDED(로그인한 채로 정지됨, ADMIN-02)면 비회원으로 그리되 사유를 함께 넘겨 머리글이 안내를 띄운다.
 * 같은 화면의 다른 API가 먼저 정지 응답을 받았으면 api 클라이언트가 적어 둔 사유(suspensionDetail)를 쓴다.
 */
export function useMe(): MeState {
  const [state, setState] = useState<MeState>({ status: 'loading' })
  useEffect(() => {
    let active = true
    api<Me>('/api/me', { allowAnonymous: true })
      .then((me) => active && setState({ status: 'member', me }))
      .catch((error: unknown) => {
        if (!active) {
          return
        }
        // 다른 API가 먼저 403을 받아 쿠키가 지워졌으면 /api/me는 401이다. 그때도 적어 둔 사유를 쓴다
        const detail = error instanceof ApiError && error.code === 'MEMBER_SUSPENDED' ? error.detail : suspensionDetail()
        setState(detail
          ? { status: 'anonymous', suspension: detail as unknown as SuspensionDetail }
          : { status: 'anonymous' })
      })
    return () => {
      active = false
    }
  }, [])
  return state
}

/** 로그인이 필요한 화면이 로그인 화면으로 보내도 되는가. 정지 안내를 띄워야 하면 보내지 않는다 */
export function shouldRedirectToLogin(me: MeState): boolean {
  return me.status === 'anonymous' && !me.suspension
}
