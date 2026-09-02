import assert from 'node:assert/strict'
import test from 'node:test'

import { ApiError } from '../src/api/client.ts'
import {
  manualDiscoveryDateWindow,
  manualNewsRefreshErrorMessage,
  manualNewsRefreshResultMessage,
  runManualNewsRefresh,
} from '../src/features/watchlists/manualNewsRefresh.ts'
import type { WatchlistDiscoveryRunResponse } from '../src/types/watchlistDiscovery.ts'

test('does not run discovery until the explicit manual action is invoked', async () => {
  const requests: unknown[] = []
  const guard = { current: false }
  const discover = async (request: unknown) => {
    requests.push(request)
    return response()
  }

  assert.equal(requests.length, 0)

  await runManualNewsRefresh(23, guard, discover, new Date('2026-09-01T16:00:00Z'))

  assert.deepEqual(requests, [{
    watchlistId: 23,
    from: '2026-09-01',
    to: '2026-09-02',
    limitPerKeyword: 10,
  }])
})

test('uses Asia/Shanghai calendar days across a year boundary', () => {
  assert.deepEqual(
    manualDiscoveryDateWindow(new Date('2025-12-31T16:00:00Z')),
    { from: '2025-12-31', to: '2026-01-01' },
  )
})

test('prevents duplicate concurrent manual runs', async () => {
  const guard = { current: false }
  let calls = 0
  let finish: ((value: WatchlistDiscoveryRunResponse) => void) | undefined
  const pendingResponse = new Promise<WatchlistDiscoveryRunResponse>((resolve) => {
    finish = resolve
  })
  const discover = async () => {
    calls += 1
    return pendingResponse
  }

  const first = runManualNewsRefresh(2, guard, discover)
  const duplicate = await runManualNewsRefresh(2, guard, discover)

  assert.equal(duplicate, null)
  assert.equal(calls, 1)
  assert.equal(guard.current, true)

  finish?.(response())
  await first
  assert.equal(guard.current, false)
})

test('formats complete and partial results from real response counters', () => {
  assert.equal(
    manualNewsRefreshResultMessage(response({ discovered: 18, saved: 6, duplicates: 12 })),
    '刷新完成：发现 18 篇，新增 6 篇，重复 12 篇。',
  )
  assert.equal(
    manualNewsRefreshResultMessage(response({
      keywordsProcessed: 5,
      keywordsFailed: 2,
      discovered: 18,
      saved: 6,
      duplicates: 12,
    })),
    '刷新完成：5 个关键词成功，2 个失败；发现 18 篇，新增 6 篇，重复 12 篇。',
  )
})

test('maps quota and access errors without exposing provider details', () => {
  assert.equal(
    manualNewsRefreshErrorMessage(new ApiError(429, 'provider body')),
    '新闻服务请求过于频繁，请稍后再试。',
  )
  assert.equal(
    manualNewsRefreshErrorMessage(new ApiError(403, 'provider body')),
    '新闻服务暂时无法完成请求，可能已达到服务额度或访问受限。',
  )
  assert.equal(
    manualNewsRefreshErrorMessage(new ApiError(500, 'provider body')),
    '刷新新闻失败，请稍后重试。',
  )
})

function response(overrides: Partial<WatchlistDiscoveryRunResponse> = {}): WatchlistDiscoveryRunResponse {
  return {
    watchlistId: 2,
    watchlistName: 'Topic',
    keywordsProcessed: 3,
    keywordsFailed: 0,
    discovered: 0,
    relevanceRejected: 0,
    saved: 0,
    duplicates: 0,
    keywordMatchesCreated: 0,
    keywordMatchesExisting: 0,
    skippedUnsupportedLanguage: 0,
    skippedInvalidUrl: 0,
    postProcessingAttempted: 0,
    metadataTranslationSucceeded: 0,
    metadataTranslationFailed: 0,
    contentExtractionSucceeded: 0,
    contentExtractionFailed: 0,
    contentTranslationSucceeded: 0,
    contentTranslationFailed: 0,
    failedKeywords: [],
    keywordResults: [],
    ...overrides,
  }
}
