import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Blog, CategoryNode, PageResponse, PostSummary, Sidebar as SidebarData } from '../../api/types'
import { useBlog } from '../../app/useBlog'
import { useMe } from '../../app/useMe'
import BlogHeader from '../../components/BlogHeader'
import Pagination from '../../components/Pagination'
import { PostItem } from '../../components/PostItem'
import Sidebar from '../../components/Sidebar'
import SubscribeButton from '../../components/SubscribeButton'
import NotFoundPage from '../error/NotFoundPage'

/**
 * 블로그 메인 (BLOG-03, BLOG-04). 최신 글 10개씩과 사이드바. 카테고리 주소(/category/{id}, 미분류는 0)와
 * 태그 주소(/tag/{이름}, TAG-02)도 이 화면이다.
 * 목록·글 수는 서버가 보는 사람이 볼 수 있는 글만 준다.
 */
export default function BlogMainPage() {
  const me = useMe()
  const [blogState, setBlog] = useBlog()
  const { categoryId, tagName } = useParams()
  const [params] = useSearchParams()
  const page = Math.max(Number(params.get('page')) || 1, 1)
  const [sidebar, setSidebar] = useState<SidebarData | null>(null)
  const [posts, setPosts] = useState<PageResponse<PostSummary> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [postsNotFound, setPostsNotFound] = useState(false)

  useEffect(() => {
    api<SidebarData>('/api/blog/sidebar', { allowAnonymous: true }).then(setSidebar).catch(() => setSidebar(null))
  }, [])

  useEffect(() => {
    let active = true
    const query = new URLSearchParams({ page: String(page) })
    if (categoryId !== undefined) {
      query.set('categoryId', categoryId)
    }
    if (tagName !== undefined) {
      query.set('tag', tagName)
    }
    api<PageResponse<PostSummary>>(`/api/posts?${query}`, { allowAnonymous: true })
      .then((result) => {
        if (active) {
          setPosts(result)
          setError(null)
          setPostsNotFound(false)
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return
        }
        setPosts(null)
        if (caught instanceof ApiError && caught.status === 404) {
          setPostsNotFound(true)
        } else {
          setError(errorMessage(caught))
        }
      })
    return () => {
      active = false
    }
  }, [categoryId, tagName, page])

  if (blogState.status === 'notFound' || postsNotFound) {
    return <NotFoundPage />
  }
  if (blogState.status === 'error') {
    return <main className="page"><p className="err">{blogState.message}</p></main>
  }
  if (blogState.status === 'loading') {
    return null
  }
  const blog = blogState.blog
  const basePath = tagName !== undefined ? `/tag/${encodeURIComponent(tagName)}`
    : categoryId === undefined ? '/' : `/category/${categoryId}`

  return (
    <div className="app">
      <BlogHeader blog={blog} me={me} />
      <main className="page">
        <div className="cols">
          <div className="stack" style={{ gap: 18 }}>
            <BlogProfile blog={blog} me={me} onBlogChange={setBlog} />
            <h2 style={{ fontSize: 17 }}>
              {tagName !== undefined ? `#${tagName}` : listTitle(categoryId, sidebar)} <span className="muted num">{posts?.totalElements ?? ''}</span>
            </h2>
            {error && <p className="err">{error}</p>}
            {posts && posts.content.length === 0 && <EmptyPosts blog={blog} />}
            {posts && posts.content.length > 0 && (
              <div className="post-list">
                {posts.content.map((post) => <PostItem key={post.id} post={post} owner={blog.viewer.isOwner} />)}
              </div>
            )}
            {posts && (
              <Pagination page={posts.page} totalPages={posts.totalPages}
                          href={(number) => `${basePath}?page=${number}`} />
            )}
          </div>
          {sidebar && <Sidebar modules={sidebar.modules} />}
        </div>
      </main>
    </div>
  )
}

/** 블로그 위쪽 프로필 상자. 주인이 아니면 구독 버튼(SUB-01)이 있고, 누르면 구독자 수가 바로 바뀐다. */
function BlogProfile({ blog, me, onBlogChange }: {
  blog: Blog
  me: ReturnType<typeof useMe>
  onBlogChange: (blog: Blog) => void
}) {
  return (
    <div className="box">
      <div className="row nowrap" style={{ flexWrap: 'wrap' }}>
        {blog.profileImageUrl
          ? <img className="avatar lg" src={blog.profileImageUrl} alt="" />
          : <span className="avatar lg" />}
        <div>
          <h2 style={{ fontSize: 20 }}>{blog.name}</h2>
          <div className="small muted">
            {blog.description && <>{blog.description} · </>}구독자 <span className="num">{blog.subscriberCount}</span>명
          </div>
        </div>
        <span style={{ flex: 1 }} />
        {!blog.viewer.isOwner && (
          <SubscribeButton blogId={blog.id} me={me} subscribed={blog.viewer.subscribed}
                           onChange={(subscribed, subscriberCount) => onBlogChange({
                             ...blog, subscriberCount, viewer: { ...blog.viewer, subscribed },
                           })} />
        )}
      </div>
    </div>
  )
}


function EmptyPosts({ blog }: { blog: Blog }) {
  return (
    <div className="stack" style={{ justifyItems: 'start' }}>
      <p className="muted" style={{ margin: 0 }}>아직 글이 없습니다.</p>
      {blog.viewer.isOwner && <Link className="btn primary" to="/manage/write">첫 글 쓰기</Link>}
    </div>
  )
}

/** "전체 글", 카테고리 이름, "미분류". 카테고리 이름은 사이드바의 트리에서 찾는다. */
function listTitle(categoryId: string | undefined, sidebar: SidebarData | null): string {
  if (categoryId === undefined) {
    return '전체 글'
  }
  if (categoryId === '0') {
    return '미분류'
  }
  const tree = sidebar?.modules.find((module) => module.type === 'CATEGORY')
  const found = tree?.type === 'CATEGORY' ? findCategory(tree.data.categories, Number(categoryId)) : undefined
  return found?.name ?? '카테고리'
}

function findCategory(categories: CategoryNode[], id: number): CategoryNode | undefined {
  for (const category of categories) {
    if (category.id === id) {
      return category
    }
    const child = findCategory(category.children, id)
    if (child) {
      return child
    }
  }
  return undefined
}
