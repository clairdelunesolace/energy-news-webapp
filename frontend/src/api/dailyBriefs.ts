import type { DailyBriefAnalysisResponse, DailyBriefResponse } from '../types/dailyBriefs'
import { ApiError, fetchJson } from './client'

export function getDailyBrief(watchlistId: number, date: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ watchlistId: String(watchlistId), date })
  return fetchJson<DailyBriefResponse>(`/api/daily-briefs?${query}`, { signal })
}

export async function getDailyBriefAnalysis(id: number, signal?: AbortSignal) {
  try {
    return await fetchJson<DailyBriefAnalysisResponse>(`/api/daily-briefs/${id}/analysis`, { signal })
  } catch (error: unknown) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

export function generateDailyBriefAnalysis(id: number, signal?: AbortSignal) {
  return fetchJson<DailyBriefAnalysisResponse>(`/api/daily-briefs/${id}/analysis/generate`, {
    method: 'POST',
    signal,
  })
}
