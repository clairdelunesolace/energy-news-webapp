import { fetchJson } from './client'
import type { ArticlePageResponse, ArticleResponse } from '../types/articles'

export interface GetArticlesParams {
  page?: number
  size?: number
  sourceId?: number
  keyword?: string
  keywordId?: number
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
  if (params.keywordId !== undefined) searchParams.set('keywordId', String(params.keywordId))

  const query = searchParams.toString()
  const url = query ? `/api/articles?${query}` : '/api/articles'

  return fetchJson<ArticlePageResponse>(url, { signal })
}

export function getArticle(id: number, signal?: AbortSignal): Promise<ArticleResponse> {
  return fetchJson<ArticleResponse>(`/api/articles/${id}`, { signal })
}
