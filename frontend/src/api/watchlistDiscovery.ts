import type {
  WatchlistDiscoveryRunRequest,
  WatchlistDiscoveryRunResponse,
} from '../types/watchlistDiscovery'
import { fetchJson } from './client'

export function runWatchlistDiscovery(
  request: WatchlistDiscoveryRunRequest,
): Promise<WatchlistDiscoveryRunResponse> {
  return fetchJson<WatchlistDiscoveryRunResponse>('/api/watchlist-discovery/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}
