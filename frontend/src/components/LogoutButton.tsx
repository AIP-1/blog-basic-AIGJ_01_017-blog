import { api } from '../api/client'
import { platformUrl } from '../app/host'

/**
 * 로그아웃 (AUTH-02). 서버가 .{플랫폼} 쿠키를 지우므로 모든 블로그 주소에서 함께 로그아웃된다.
 * 끝나면 플랫폼 홈으로 간다.
 */
export default function LogoutButton() {
  async function logout() {
    try {
      await api('/api/auth/logout', { method: 'POST', allowAnonymous: true })
    } finally {
      window.location.assign(platformUrl('/'))
    }
  }
  return (
    <button className="btn ghost" type="button" onClick={logout}>로그아웃</button>
  )
}
