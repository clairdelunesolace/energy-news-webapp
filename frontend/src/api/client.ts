interface ProblemResponse {
  detail?: unknown
}

export interface CsrfTokenResponse {
  token: string
  headerName: string
}

export const UNAUTHORIZED_EVENT = 'energy-news:unauthorized'

let csrfToken: CsrfTokenResponse | null = null

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function getOrCreateCsrfToken(): Promise<CsrfTokenResponse> {
  if (csrfToken) return csrfToken

  const response = await fetch('/api/auth/csrf', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) throw await createApiError(response)

  csrfToken = (await response.json()) as CsrfTokenResponse
  return csrfToken
}

export function clearCsrfToken() {
  csrfToken = null
}

export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  const method = (init?.method ?? 'GET').toUpperCase()

  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrf = await getOrCreateCsrfToken()
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(input, {
    ...init,
    credentials: 'same-origin',
    headers,
  })

  if (response.status === 401 && !isAuthEndpoint(input)) {
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }

  return response
}

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await apiFetch(input, init)

  if (!response.ok) throw await createApiError(response)

  return response.json() as Promise<T>
}

export async function requireSuccessful(response: Response): Promise<void> {
  if (!response.ok) throw await createApiError(response)
}

async function createApiError(response: Response): Promise<ApiError> {
  let detail: string | undefined

  try {
    const problem = (await response.json()) as ProblemResponse
    if (typeof problem.detail === 'string') {
      detail = problem.detail
    }
  } catch {
    // A non-JSON error response still becomes a clear ApiError below.
  }

  return new ApiError(response.status, detail ?? `请求失败（HTTP ${response.status}）`)
}

function isAuthEndpoint(input: RequestInfo | URL): boolean {
  if (typeof input === 'string') return input.startsWith('/api/auth/')
  if (input instanceof URL) return input.pathname.startsWith('/api/auth/')

  try {
    return new URL(input.url, window.location.origin).pathname.startsWith('/api/auth/')
  } catch {
    return false
  }
}
