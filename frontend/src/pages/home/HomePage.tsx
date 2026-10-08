import { useMe } from '../../app/useMe'
import PlatformHeader from '../../components/PlatformHeader'

/** 플랫폼 홈. 최신 글·인기 글은 스텝 6에서 채운다. */
export default function HomePage() {
  const me = useMe()
  return (
    <div className="app">
      <PlatformHeader me={me} />
      <main className="page">
        <h1 style={{ fontSize: 22 }}>블로그</h1>
        <p className="muted">홈의 최신 글·인기 글은 스텝 6에서 만듭니다.</p>
      </main>
    </div>
  )
}
