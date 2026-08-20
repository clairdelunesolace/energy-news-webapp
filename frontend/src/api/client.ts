interface ProblemResponse {
  detail?: unknown
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    let detail: string | undefined

    try {
      const problem = (await response.json()) as ProblemResponse
      if (typeof problem.detail === 'string') {
        detail = problem.detail
      }
    } catch {
      // A non-JSON error response still becomes a clear ApiError below.
    }

    throw new ApiError(response.status, detail ?? `请求失败（HTTP ${response.status}）`)
  }

  return response.json() as Promise<T>
}
