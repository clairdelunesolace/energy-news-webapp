export interface WatchlistDiscoveryRunRequest {
  watchlistId: number
  from: string
  to: string
  limitPerKeyword: number
}

export interface WatchlistDiscoveryKeywordFailure {
  keywordId: number
  keyword: string
  message: string
}

export interface WatchlistDiscoveryKeywordResult {
  keywordId: number
  keyword: string
  discovered: number
  relevanceRejected: number
  saved: number
  duplicates: number
  keywordMatchesCreated: number
  keywordMatchesExisting: number
  skippedUnsupportedLanguage: number
  skippedInvalidUrl: number
  failure: string | null
}

export interface WatchlistDiscoveryRunResponse {
  watchlistId: number
  watchlistName: string
  keywordsProcessed: number
  keywordsFailed: number
  discovered: number
  relevanceRejected: number
  saved: number
  duplicates: number
  keywordMatchesCreated: number
  keywordMatchesExisting: number
  skippedUnsupportedLanguage: number
  skippedInvalidUrl: number
  postProcessingAttempted: number
  metadataTranslationSucceeded: number
  metadataTranslationFailed: number
  contentExtractionSucceeded: number
  contentExtractionFailed: number
  contentTranslationSucceeded: number
  contentTranslationFailed: number
  failedKeywords: WatchlistDiscoveryKeywordFailure[]
  keywordResults: WatchlistDiscoveryKeywordResult[]
}
