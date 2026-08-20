import { fetchJson } from './client'
import type { ArticlePageResponse } from '../types/articles'

export interface GetArticlesParams {
  page?: number
  size?: number
  sourceId?: number
  keyword?: string
}

export function getArticles(
  params: GetArticlesParams = {},
  signal?: AbortSignal,
): Promise<ArticlePageResponse> {
  const searchParams = new URLSearchParams()

  if (params.page !== undefined) searchParams.set('page', String(params.page))
  if (params.size !== undefined) searchParams.set('size', String(params.size))
  if (params.sourceId !== undefined) searchParams.set('sourceId', String(params.sourceId))
  if (params.keyword !== undefined) searchParams.set('keyword', params.keyword)

  const query = searchParams.toString()
  const url = query ? `/api/articles?${query}` : '/api/articles'

  return fetchJson<ArticlePageResponse>(url, { signal })
}
