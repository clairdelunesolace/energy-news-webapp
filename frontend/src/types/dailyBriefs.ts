export interface DailyBriefItemResponse {
  rank: number
  articleId: number
  title: string
  description: string | null
  url: string
  sourceName: string
  publishedAt: string | null
  effectiveTime: string
  matchingKeywordCount: number
  matchedKeywords: string[]
}

export interface DailyBriefResponse {
  id: number
  watchlistId: number
  watchlistName: string
  briefDate: string
  zone: string
  windowStart: string
  windowEnd: string
  candidateCount: number
  itemCount: number
  createdAt: string
  updatedAt: string
  items: DailyBriefItemResponse[]
}

export interface DailyBriefEventResponse {
  rank: number
  title: string
  summary: string
  whyItMatters: string
  supportingArticleIds: number[]
}

export interface DailyBriefAnalysisResponse {
  id: number
  dailyBriefId: number
  provider: string
  model: string
  headline: string
  overview: string
  generatedAt: string
  createdAt: string
  updatedAt: string
  events: DailyBriefEventResponse[]
}
