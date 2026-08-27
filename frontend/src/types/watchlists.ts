export interface KeywordResponse {
  id: number
  keyword: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface WatchlistResponse {
  id: number
  name: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  keywords: KeywordResponse[]
}

export interface UpdateWatchlistRequest {
  name?: string
  enabled?: boolean
}

export interface UpdateKeywordRequest {
  keyword?: string
  enabled?: boolean
}
