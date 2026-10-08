import { useSearchParams } from 'react-router'

export default function LoginPage() {
  const [params] = useSearchParams()
  return (
    <main>
      <h1>로그인</h1>
      <p>로그인 화면은 스텝 4에서 만듭니다.</p>
      {params.get('redirect') && <p>로그인 뒤 돌아갈 주소: {params.get('redirect')}</p>}
    </main>
  )
}
