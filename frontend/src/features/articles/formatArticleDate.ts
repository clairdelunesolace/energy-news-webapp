const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

export function formatArticleDate(value: string): string {
  return dateFormatter.format(new Date(value))
}
