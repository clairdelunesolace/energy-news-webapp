import { ArticlePreviewList } from '../features/articles/ArticlePreviewList'

export function HomePage() {
  return (
    <main className="page-shell">
      <section aria-labelledby="latest-news-heading">
        <p className="page-eyebrow">能源转型 · 行业动态</p>
        <h1 id="latest-news-heading">最新资讯</h1>
        <p className="page-introduction">聚合储能行业动态，中文译文优先呈现，英文原文随时可查。</p>
        <ArticlePreviewList />
      </section>
    </main>
  )
}
