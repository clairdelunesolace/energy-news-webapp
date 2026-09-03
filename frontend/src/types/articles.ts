export type SourceLanguage = 'EN' | 'ZH_CN'

export type TranslationLanguage = 'ZH_CN'

export interface ArticleSourceResponse {
  id: number
  name: string
}

export interface ArticleOriginalResponse {
  language: SourceLanguage
  title: string
  description: string | null
  content: string | null
}

export interface ArticleTranslationResponse {
  language: TranslationLanguage
  title: string | null
  description: string | null
  content: string | null
}

export interface ArticleResponse {
  id: number
  source: ArticleSourceResponse
  url: string
  publishedAt: string | null
  collectedAt: string
  original: ArticleOriginalResponse
  translation: ArticleTranslationResponse | null
  createdAt: string
  updatedAt: string
  tags: string[]
}

export type ArticlePostProcessingStepStatus = 'SUCCESS' | 'FAILED' | 'NOT_AVAILABLE'

export type ArticlePostProcessingOverallStatus =
  | 'SUCCESS'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'

export interface ArticlePostProcessingBackfillResponse {
  articleId: number
  metadataTranslationStatus: ArticlePostProcessingStepStatus
  contentExtractionStatus: ArticlePostProcessingStepStatus
  contentTranslationStatus: ArticlePostProcessingStepStatus
  overallStatus: ArticlePostProcessingOverallStatus
}

export interface ArticlePageResponse {
  content: ArticleResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
