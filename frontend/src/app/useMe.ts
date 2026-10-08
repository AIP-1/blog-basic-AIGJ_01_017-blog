import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Me } from '../api/types'

export type MeState = { status: 'loading' } | { status: 'anonymous' } | { status: 'member'; me: Me }

/**
 * 로그인 상태. GET /api/me가 401이면 비회원이다.
 * 로그인 쿠키는 HttpOnly라 자바스크립트가 직접 읽을 수 없어서 서버에 물어본다.
 */
export function useMe(): MeState {
  const [state, setState] = useState<MeState>({ status: 'loading' })
  useEffect(() => {
    let active = true
    api<Me>('/api/me', { allowAnonymous: true })
      .then((me) => active && setState({ status: 'member', me }))
      .catch(() => active && setState({ status: 'anonymous' }))
    return () => {
      active = false
    }
  }, [])
  return state
}
