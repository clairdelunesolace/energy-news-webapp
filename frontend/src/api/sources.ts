import { fetchJson } from './client'
import type { SourceResponse } from '../types/sources'

export function getSources(signal?: AbortSignal): Promise<SourceResponse[]> {
  return fetchJson<SourceResponse[]>('/api/sources', { signal })
}
