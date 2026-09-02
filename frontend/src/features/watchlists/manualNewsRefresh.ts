import { ApiError } from '../../api/client.ts'
import type {
  WatchlistDiscoveryRunRequest,
  WatchlistDiscoveryRunResponse,
} from '../../types/watchlistDiscovery.ts'

const DISCOVERY_TIME_ZONE = 'Asia/Shanghai'
const LIMIT_PER_KEYWORD = 10

export interface ManualNewsRefreshGuard {
  current: boolean
}

type RunDiscovery = (
  request: WatchlistDiscoveryRunRequest,
) => Promise<WatchlistDiscoveryRunResponse>

export async function runManualNewsRefresh(
  watchlistId: number,
  guard: ManualNewsRefreshGuard,
  runDiscovery: RunDiscovery,
  now = new Date(),
): Promise<WatchlistDiscoveryRunResponse | null> {
  if (guard.current) return null

  guard.current = true
  try {
    const { from, to } = manualDiscoveryDateWindow(now)
    return await runDiscovery({ watchlistId, from, to, limitPerKeyword: LIMIT_PER_KEYWORD })
  } finally {
    guard.current = false
  }
}

export function manualDiscoveryDateWindow(now = new Date()): { from: string; to: string } {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: DISCOVERY_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value)
  const currentDate = new Date(Date.UTC(value('year'), value('month') - 1, value('day')))
  const to = currentDate.toISOString().slice(0, 10)
  currentDate.setUTCDate(currentDate.getUTCDate() - 1)
  return { from: currentDate.toISOString().slice(0, 10), to }
}

export function manualNewsRefreshResultMessage(response: WatchlistDiscoveryRunResponse): string {
  if (response.keywordsFailed > 0) {
    return `刷新完成：${response.keywordsProcessed} 个关键词成功，${response.keywordsFailed} 个失败；发现 ${response.discovered} 篇，新增 ${response.saved} 篇，重复 ${response.duplicates} 篇。`
  }
  return `刷新完成：发现 ${response.discovered} 篇，新增 ${response.saved} 篇，重复 ${response.duplicates} 篇。`
}

export function manualNewsRefreshErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return '新闻服务请求过于频繁，请稍后再试。'
    if (error.status === 403 || error.status === 503) {
      return '新闻服务暂时无法完成请求，可能已达到服务额度或访问受限。'
    }
  }
  return '刷新新闻失败，请稍后重试。'
}
