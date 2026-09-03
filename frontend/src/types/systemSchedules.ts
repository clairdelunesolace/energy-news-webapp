export interface ScheduleResponse {
  enabled: boolean
  cron: string
  zone: string
  dailyTime: string | null
}

export interface SystemSchedulesResponse {
  newsDiscovery: ScheduleResponse
  dailyBrief: ScheduleResponse
}
