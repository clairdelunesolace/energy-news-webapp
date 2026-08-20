import { ArticleFeed } from '../features/articles/ArticleFeed'

export function HomePage() {
  return (
    <main className="page-shell">
      <section className="feed-page" aria-labelledby="latest-news-heading">
        <header className="feed-page__heading">
          <h1 id="latest-news-heading">最新资讯</h1>
          <p>聚合储能行业动态，中文译文优先呈现，英文原文清晰可查。</p>
        </header>
        <ArticleFeed />
      </section>
    </main>
  )
}
