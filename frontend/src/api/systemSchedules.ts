import { fetchJson } from './client'
import type { SystemSchedulesResponse } from '../types/systemSchedules'

export async function getSystemSchedules(signal?: AbortSignal): Promise<SystemSchedulesResponse | null> {
  try {
    return await fetchJson<SystemSchedulesResponse>('/api/system/schedules', { signal })
  } catch {
    // Optional metadata must not interrupt the page's normal loading or actions.
    return null
  }
}
