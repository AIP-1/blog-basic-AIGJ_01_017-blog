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

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
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
    return (response.status === 204 ? undefined : await response.json()) as T
  }

  const error = new ApiError(response.status, await readError(response))
  if (response.status === 401 && !options.allowAnonymous) {
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

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

/** 로그인 화면 주소. 로그인 뒤 returnTo로 돌아온다 */
export function loginUrl(returnTo: string = window.location.href): string {
  return platformUrl(`/login?redirect=${encodeURIComponent(returnTo)}`)
}

export function redirectToLogin(returnTo?: string): void {
  window.location.assign(loginUrl(returnTo))
}
