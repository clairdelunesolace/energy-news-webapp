import type { SourceLanguage } from './articles'

export type SourceType = 'RSS' | 'API' | 'WEBSITE'

export type SourcePriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface SourceResponse {
  id: number
  name: string
  url: string
  type: SourceType
  priority: SourcePriority
  language: SourceLanguage
  enabled: boolean
  createdAt: string
  updatedAt: string
}
