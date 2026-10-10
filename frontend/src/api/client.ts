// API 호출 공통 처리 (T015, contracts/rest-api.md 공통)
// - 로그인 쿠키는 브라우저가 자동으로 보낸다(같은 출처, HttpOnly)
// - 상태를 바꾸는 요청에는 CSRF 대책 헤더 X-Requested-With를 붙인다
// - 401이면 플랫폼 로그인 화면으로 보내고, 로그인 뒤 지금 주소로 돌아온다

import { platformUrl } from '../app/host'

export interface FieldError {
  field: string
  reason: string
}

export interface ErrorBody {
  code: string
  message: string
  fieldErrors?: FieldError[]
  detail?: Record<string, unknown>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly detail?: Record<string, unknown>

  constructor(status: number, body: ErrorBody) {
    super(body.message)
    this.status = status
    this.code = body.code
    this.fieldErrors = body.fieldErrors ?? []
    this.detail = body.detail
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  method?: Method
  body?: unknown
  /** 연타 방지 대상(글 발행, 댓글·방명록 작성)에 넣는다. newIdempotencyKey()로 만든다 */
  idempotencyKey?: string
  /** true면 401이어도 로그인 화면으로 보내지 않는다(로그인 여부만 확인할 때) */
  allowAnonymous?: boolean
}

const UNKNOWN_ERROR: ErrorBody = {
  code: 'INTERNAL_ERROR',
  message: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
}

/**
 * 로그인한 채로 정지된 회원(ADMIN-02)이 받은 정지 사유. 서버는 정지 회원의 API 요청마다 403 MEMBER_SUSPENDED를 주고
 * 로그인 쿠키를 지운다. 한 화면이 API 여러 개를 동시에 부르면 모두 403이 되므로, 사유는 여기 한 번 적어 두고(useMe가 읽음)
 * 읽기 요청(GET)은 쿠키가 지워진 상태로 한 번 더 보내 비회원으로 화면을 그린다.
 */
let suspension: Record<string, unknown> | null = null

export function suspensionDetail(): Record<string, unknown> | null {
  return suspension
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(path, options, true)
}

async function request<T>(path: string, options: RequestOptions, retryIfSuspended: boolean): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-Requested-With'] = 'XMLHttpRequest'
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (response.ok) {
    // 202·204처럼 본문이 없는 성공도 있다
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  const error = new ApiError(response.status, await readError(response))
  if (error.code === 'MEMBER_SUSPENDED' && path !== '/api/auth/login') {
    suspension = error.detail ?? {}
    // 내 정보(/api/me)는 그대로 실패시켜 useMe가 안내를 띄우게 하고, 다른 읽기는 비회원으로 다시 받는다
    if (retryIfSuspended && method === 'GET' && path !== '/api/me') {
      return request<T>(path, options, false)
    }
  }
  // 정지 안내를 띄워야 하면 로그인 화면으로 보내지 않는다
  if (response.status === 401 && !options.allowAnonymous && !suspension) {
    redirectToLogin()
  }
  throw error
}

async function readError(response: Response): Promise<ErrorBody> {
  try {
    const body = (await response.json()) as Partial<ErrorBody>
    return body.code && body.message ? (body as ErrorBody) : UNKNOWN_ERROR
  } catch {
    return UNKNOWN_ERROR
  }
}

/**
 * 연타 방지 키(UUID v4). crypto.randomUUID()는 보안 컨텍스트(HTTPS, localhost)에서만 있어서
 * 개발 주소 http://alpha.blog.test에서는 없다. 그때는 어디서나 되는 crypto.getRandomValues로 만든다.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // 버전 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // RFC 4122 변형
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** 로그인 화면 주소. 로그인 뒤 returnTo로 돌아온다 */
export function loginUrl(returnTo: string = window.location.href): string {
  return platformUrl(`/login?redirect=${encodeURIComponent(returnTo)}`)
}

export function redirectToLogin(returnTo?: string): void {
  window.location.assign(loginUrl(returnTo))
}

/**
 * 파일 올리기 (multipart/form-data, T037). 본문 이미지 등.
 * Content-Type은 브라우저가 경계 문자열(boundary)과 함께 정하므로 직접 넣지 않는다.
 */
export async function uploadFile<T>(path: string, file: File, field = 'file'): Promise<T> {
  const form = new FormData()
  form.append(field, file)
  const response = await fetch(path, {
    method: 'POST',
    headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
    credentials: 'same-origin',
    body: form,
  })
  if (response.ok) {
    return (await response.json()) as T
  }
  const error = new ApiError(response.status, await readError(response))
  if (response.status === 401) {
    redirectToLogin()
  }
  throw error
}
