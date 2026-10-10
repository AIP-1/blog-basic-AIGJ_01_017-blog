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
  /** 꾸미기 (BLOG-05) */
  skin: Skin
  listLayout: 'LIST' | 'THUMBNAIL'
  accentColor: AccentColor
}

export type Skin = 'BASIC' | 'MAGAZINE' | 'NOTE'
export type AccentColor = 'BLUE' | 'GREEN' | 'ORANGE' | 'PINK' | 'PURPLE' | 'GRAY'


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

/**
 * 관리 화면 태그 한 줄 (GET /api/manage/tags). postCount는 지우지 않은 글 전부(임시저장·예약 포함),
 * publishedCount는 블로그 화면에 나오는 발행 글 수. 글이 하나도 남지 않은 태그는 서버가 지운다
 */
export interface ManagedTag {
  id: number
  name: string
  postCount: number
  publishedCount: number
}

export type SidebarModule =
  | { type: 'PROFILE'; data: { name: string; description: string | null; profileImageUrl: string | null } }
  | { type: 'CATEGORY'; data: CategoryTree }
  | { type: 'TAG'; data: TagCount[] }
  | { type: 'RECENT_POST'; data: { id: number; title: string }[] }
  | { type: 'RECENT_COMMENT'; data: RecentComment[] }
  | { type: 'VISITOR'; data: { today: number; yesterday: number; total: number } }
  | { type: 'POPULAR_POST'; data: { id: number; title: string; viewCount: number }[] }
  | { type: 'SUBSCRIBE'; data: { blogId: number; subscriberCount: number; subscribed: boolean } }

/** 사이드바 모듈 8종 (BLOG-05). 순서와 표시는 주인이 블로그 설정에서 정한다 */
export type SidebarModuleType = SidebarModule['type']

/** GET·PUT /api/blog/sidebar/modules 한 칸 */
export interface SidebarModuleItem {
  moduleType: SidebarModuleType
  isVisible: boolean
}

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
  /** 고른 대표 이미지. null이면 본문 첫 이미지 (POST-07) */
  thumbnailImageId: number | null
  /** 본문에 든 이미지(본문 순서). 대표 이미지 후보 */
  images: UploadedImage[]
  /** 예약 발행 시각 (POST-13). 예약 글이 아니면 null */
  scheduledAt: string | null
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

/** 마이페이지 내 블로그 한 줄 (GET /api/me/blogs, BLOG-08). movedTo는 이사한 블로그의 새 주소 */
export interface MyBlog {
  id: number
  address: string
  name: string
  isPrimary: boolean
  movedTo: string | null
  postCount: number
}

/** 올린 이미지 (POST /api/images) */
export interface UploadedImage {
  id: number
  url: string
  thumbnailUrl: string
}

/** 전체 검색의 블로그 한 줄 (GET /api/search?type=blog, SRCH-02) */
export interface FoundBlog {
  blog: { id: number; address: string; name: string; description: string | null; profileImageUrl: string | null }
  owner: MemberSummary
  subscriberCount: number
}

/** 관리 화면의 대상 한 줄 (신고 묶음, 관리 이력). 링크는 app/admin.ts의 targetHref가 만든다 */
export interface ModerationTarget {
  type: 'POST' | 'COMMENT' | 'BLOG' | 'MEMBER' | 'REPORT'
  id: number
  label: string | null
  blogAddress: string | null
  postId: number | null
  memberId: number | null
  /** false면 지워진 대상 */
  exists: boolean
  /** 지금 숨김·제한·정지 중인가 */
  sanctioned: boolean
}

/** 관리 이력 한 줄 (GET /api/admin/moderation-logs) */
export interface ModerationLogLine {
  id: number
  createdAt: string
  admin: { id: number; nickname: string }
  action: string
  reason: string | null
  reasonMessage: string | null
  reasonDetail: string | null
  target: ModerationTarget
}

export interface AdminDashboard {
  todaySignups: number
  todayPosts: number
  pendingReports: number
  recentModerations: ModerationLogLine[]
}

/** 관리자 회원 목록 한 줄. status는 지금 기준 */
export interface AdminMember {
  id: number
  nickname: string
  email: string | null
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'
  suspendedUntil: string | null
  createdAt: string
}

export interface AdminMemberDetail {
  member: AdminMember
  blogs: { id: number; address: string; name: string; isPrimary: boolean; deleted: boolean; restricted: boolean }[]
  reportCount: number
  suspension: { reason: string | null; reasonMessage: string | null; suspendedUntil: string | null } | null
  moderations: ModerationLogLine[]
}

/** 처리 대기 신고의 대상별 묶음 */
export interface ReportGroup {
  targetType: 'POST' | 'COMMENT' | 'BLOG'
  targetId: number
  targetPreview: string | null
  target: ModerationTarget
  reportCount: number
  reasons: Record<string, number>
  firstReportedAt: string
}

export interface TargetReports {
  target: ModerationTarget
  reports: {
    id: number
    reporter: { id: number; nickname: string } | null
    reason: string
    reasonMessage: string
    description: string | null
    status: 'PENDING' | 'DONE'
    result: string | null
    createdAt: string
    processedAt: string | null
  }[]
}

/** 공지 목록 한 줄, 홈 상단 최신 공지 */
export interface NoticeSummary {
  id: number
  title: string
  createdAt: string
}

export interface Notice extends NoticeSummary {
  content: string
  updatedAt: string
}
