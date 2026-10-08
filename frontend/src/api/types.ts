// 서버 응답 모양 (contracts/rest-api.md 주요 응답 객체)

export interface BlogRef {
  id: number
  address: string
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

export type SidebarModule =
  | { type: 'PROFILE'; data: { name: string; description: string | null; profileImageUrl: string | null } }
  | { type: 'CATEGORY'; data: CategoryTree }
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
