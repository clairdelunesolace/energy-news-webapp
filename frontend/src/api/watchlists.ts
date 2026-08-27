import type {
  KeywordResponse,
  UpdateKeywordRequest,
  UpdateWatchlistRequest,
  WatchlistResponse,
} from '../types/watchlists'
import { apiFetch, fetchJson, requireSuccessful } from './client'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function getWatchlists(signal?: AbortSignal): Promise<WatchlistResponse[]> {
  return fetchJson<WatchlistResponse[]>('/api/watchlists', { signal })
}

export function createWatchlist(name: string): Promise<WatchlistResponse> {
  return fetchJson<WatchlistResponse>('/api/watchlists', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ name }),
  })
}

export function updateWatchlist(
  id: number,
  request: UpdateWatchlistRequest,
): Promise<WatchlistResponse> {
  return fetchJson<WatchlistResponse>(`/api/watchlists/${id}`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify(request),
  })
}

export async function deleteWatchlist(id: number): Promise<void> {
  const response = await apiFetch(`/api/watchlists/${id}`, { method: 'DELETE' })
  await requireSuccessful(response)
}

export function addKeyword(watchlistId: number, keyword: string): Promise<KeywordResponse> {
  return fetchJson<KeywordResponse>(`/api/watchlists/${watchlistId}/keywords`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ keyword }),
  })
}

export function updateKeyword(
  id: number,
  request: UpdateKeywordRequest,
): Promise<KeywordResponse> {
  return fetchJson<KeywordResponse>(`/api/keywords/${id}`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify(request),
  })
}

export async function deleteKeyword(id: number): Promise<void> {
  const response = await apiFetch(`/api/keywords/${id}`, { method: 'DELETE' })
  await requireSuccessful(response)
}
