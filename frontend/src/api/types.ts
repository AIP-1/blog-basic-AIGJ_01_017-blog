// 서버 응답 모양 (contracts/rest-api.md 주요 응답 객체)

export interface BlogRef {
  id: number
  address: string
  name: string
}

/** 고정 주제 (GET /api/topics). code는 글 저장 본문의 topic 값 */
export interface Topic {
  code: string
  name: string
}

export interface Me {
  id: number
  email: string | null
  nickname: string
  profileImageUrl: string | null
  role: 'USER' | 'ADMIN'
  hasPassword: boolean
  primaryBlog: BlogRef | null
  unreadNotificationCount: number
}

export interface MemberSummary {
  id: number
  nickname: string
  profileImageUrl: string | null
  primaryBlogAddress: string | null
}

export interface Blog {
  id: number
  address: string
  name: string
  description: string | null
  profileImageUrl: string | null
  owner: MemberSummary
  postCount: number
  subscriberCount: number
  viewer: { isOwner: boolean; subscribed: boolean }
  restriction: { reason: string | null; reasonMessage: string | null } | null
}

/** 홈 인기 글 (GET /api/home/popular). snapshotAt은 순위를 계산한 시각(5분마다) */
export interface PopularPosts {
  snapshotAt: string
  items: { rank: number; post: PostSummary }[]
}

export interface PostSummary {
  id: number
  title: string
  summary: string | null
  thumbnailUrl: string | null
  blog: BlogRef
  category: { id: number; name: string } | null
  publishedAt: string
  likeCount: number
  commentCount: number
}

/** 내 글 관리 목록 한 줄 (GET /api/manage/posts). PostSummary에 주인용 칸이 더 붙는다 */
export interface ManagedPostSummary {
  id: number
  title: string
  thumbnailUrl: string | null
  category: { id: number; name: string } | null
  topic: string | null
  publishedAt: string | null
  updatedAt: string
  likeCount: number
  commentCount: number
  viewCount: number
  status: 'PUBLISHED' | 'DRAFT' | 'SCHEDULED'
  visibility: 'PUBLIC' | 'PRIVATE' | 'SUBSCRIBERS'
  scheduledAt: string | null
  blinded: boolean
  blind: { reason: string; reasonMessage: string } | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CategoryNode {
  id: number
  name: string
  isPrivate?: boolean
  postCount: number
  sortOrder: number
  children: CategoryNode[]
}

export interface CategoryTree {
  totalCount: number
  uncategorizedCount: number
  categories: CategoryNode[]
}

export interface RecentComment {
  id: number
  postId: number
  content: string | null
  authorNickname: string | null
  state: 'NORMAL' | 'SECRET' | 'BLINDED'
}

/** 태그 목록 한 줄 (GET /api/tags, 사이드바 TAG). 보는 사람이 볼 수 있는 글 수, 많은 순 */
export interface TagCount {
  id: number
  name: string
  postCount: number
}

export type SidebarModule =
  | { type: 'PROFILE'; data: { name: string; description: string | null; profileImageUrl: string | null } }
  | { type: 'CATEGORY'; data: CategoryTree }
  | { type: 'TAG'; data: TagCount[] }
  | { type: 'RECENT_POST'; data: { id: number; title: string }[] }
  | { type: 'RECENT_COMMENT'; data: RecentComment[] }

export interface Sidebar {
  modules: SidebarModule[]
}

/** 403 MEMBER_SUSPENDED의 detail */
export interface SuspensionDetail {
  reason: string | null
  reasonMessage: string | null
  suspendedUntil: string | null
}

/** 편집용 글 (GET /api/manage/posts/{id}) */
export interface ManagedPost {
  id: number
  title: string
  contentHtml: string
  tagNames: string[]
  categoryId: number | null
  topic: string | null
  visibility: 'PUBLIC' | 'PRIVATE' | 'SUBSCRIBERS'
  status: 'DRAFT' | 'PUBLISHED' | 'SCHEDULED'
  publishedAt: string | null
  updatedAt: string | null
  commentAllowed: boolean
  blind: { reason: string; reasonMessage: string } | null
}

/** 발행·수정 응답 */
export interface PostSaved {
  id: number
  status: string
  url: string
}

export interface CursorResponse<T> {
  content: T[]
  nextCursor: string | null
}

/** 글 상세 (GET /api/posts/{id}) */
export interface PostDetail {
  id: number
  blog: BlogRef
  title: string
  contentHtml: string
  category: { id: number; name: string } | null
  tags: string[]
  topic: string | null
  visibility: 'PUBLIC' | 'PRIVATE' | 'SUBSCRIBERS'
  publishedAt: string | null
  updatedAt: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  commentAllowed: boolean
  author: MemberSummary
  viewer: { isOwner: boolean; liked: boolean; bookmarked: boolean }
  blind: { reason: string; reasonMessage: string } | null
  prev: { id: number; title: string } | null
  next: { id: number; title: string } | null
}

/** 댓글 (contracts Comment) */
export interface Comment {
  id: number
  parentId: number | null
  author: MemberSummary | null
  content: string | null
  secret: boolean
  state: 'NORMAL' | 'SECRET' | 'DELETED' | 'BLINDED'
  createdAt: string
  updatedAt: string | null
  viewer: { canEdit: boolean; canDelete: boolean }
  blind: { reason: string; reasonMessage: string } | null
  replies: Comment[]
}

export interface CommentList extends CursorResponse<Comment> {
  totalCount: number
}

/** 올린 이미지 (POST /api/images) */
export interface UploadedImage {
  id: number
  url: string
  thumbnailUrl: string
}
