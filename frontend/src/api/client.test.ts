import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, newIdempotencyKey, suspensionDetail } from './client'

const assign = vi.fn()

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  vi.stubGlobal('window', {
    location: { protocol: 'http:', port: '8080', href: 'http://alpha.blog.test:8080/manage', assign },
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  assign.mockReset()
})

describe('api', () => {
  it('GET은 CSRF 헤더 없이 보내고 JSON을 돌려준다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api('/api/posts/1')).resolves.toEqual({ id: 1 })
    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['X-Requested-With']).toBeUndefined()
    expect(init.credentials).toBe('same-origin')
  })

  it('상태를 바꾸는 요청에는 CSRF 헤더와 연타 방지 키를 붙인다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { id: 2 }))
    vi.stubGlobal('fetch', fetchMock)
    const key = newIdempotencyKey()

    await api('/api/posts', { method: 'POST', body: { title: 't' }, idempotencyKey: key })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest')
    expect(init.headers['Idempotency-Key']).toBe(key)
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"title":"t"}')
  })

  it('204는 본문 없이 끝난다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(api('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('본문 없는 200·202도 성공이다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 202 })))

    await expect(api('/api/auth/email-verifications', { method: 'POST', body: {} })).resolves.toBeUndefined()
  })

  it('randomUUID가 없는 주소(HTTP 개발 주소)에서도 UUID v4를 만든다', () => {
    vi.stubGlobal('crypto', { getRandomValues: (array: Uint8Array) => array.fill(0xab) })

    expect(newIdempotencyKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })

  it('오류는 COM-02 본문을 담은 ApiError다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(400, {
      code: 'VALIDATION_FAILED',
      message: '입력값을 확인해 주세요.',
      fieldErrors: [{ field: 'title', reason: '제목을 입력해 주세요.' }],
    })))

    const error = (await api('/api/posts', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldErrors[0].field).toBe('title')
  })

  it('401이면 플랫폼 로그인 화면으로 보내고 지금 주소로 돌아오게 한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {
      code: 'UNAUTHORIZED', message: '로그인이 필요합니다.',
    })))

    await expect(api('/api/me')).rejects.toBeInstanceOf(ApiError)
    expect(assign).toHaveBeenCalledWith(
      'http://blog.test:8080/login?redirect=' + encodeURIComponent('http://alpha.blog.test:8080/manage'))
  })

  it('allowAnonymous면 401이어도 이동하지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {
      code: 'UNAUTHORIZED', message: '로그인이 필요합니다.',
    })))

    await expect(api('/api/me', { allowAnonymous: true })).rejects.toBeInstanceOf(ApiError)
    expect(assign).not.toHaveBeenCalled()
  })

  it('JSON이 아닌 오류는 일반 오류 문구로 바꾼다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>', { status: 502 })))

    const error = (await api('/api/posts').catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('INTERNAL_ERROR')
  })
})

// 정지 사유는 모듈 변수에 남으므로 이 묶음은 파일 맨 끝에 둔다(앞 테스트의 401 이동에 영향 없게)
describe('로그인한 채로 정지된 회원 (ADMIN-02)', () => {
  const suspended = jsonResponse(403, {
    code: 'MEMBER_SUSPENDED', message: '이용이 정지된 계정입니다.',
    detail: { reason: 'SPAM', reasonMessage: '스팸', suspendedUntil: null },
  })

  it('읽기 요청은 사유를 적어 두고 쿠키가 지워진 상태로 한 번 더 보내 비회원 응답을 받는다', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(suspended).mockResolvedValueOnce(jsonResponse(200, { id: 1 }))
    vi.stubGlobal('fetch', fetch)

    await expect(api('/api/posts/1', { allowAnonymous: true })).resolves.toEqual({ id: 1 })
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(suspensionDetail()).toEqual({ reason: 'SPAM', reasonMessage: '스팸', suspendedUntil: null })
  })

  it('내 정보는 다시 보내지 않고 실패시켜 useMe가 안내를 띄운다', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(403, {
      code: 'MEMBER_SUSPENDED', message: '이용이 정지된 계정입니다.', detail: {},
    }))
    vi.stubGlobal('fetch', fetch)

    await expect(api('/api/me', { allowAnonymous: true })).rejects.toBeInstanceOf(ApiError)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('정지 안내를 띄울 때는 401이어도 로그인 화면으로 보내지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {
      code: 'UNAUTHORIZED', message: '로그인이 필요합니다.',
    })))

    await expect(api('/api/manage/posts')).rejects.toBeInstanceOf(ApiError)
    expect(assign).not.toHaveBeenCalled()
  })
})
