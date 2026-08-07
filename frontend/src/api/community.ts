// 社区模块请求层：封装帖子、点赞、评论等社区互动接口调用。
import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type {
  PostVO,
  PostPageResult,
  CommentVO,
  CommentPageResult,
  CommentLikeResult,
  CommentSort,
} from './types'

export const communityApi = {
  posts: (params: {
    category?: string
    sort?: string
    pageNum?: number
    pageSize?: number
  } = {}) => apiGet<PostPageResult>('/community/posts', params),

  postDetail: (id: number) => apiGet<PostVO>(`/community/posts/${id}`),

  createPost: (data: Partial<PostVO>) => apiPost<void>('/community/posts', data),

  deletePost: (id: number) => apiDelete<void>(`/community/posts/${id}`),

  // F-10：点赞/取消点赞幂等切换，返回当前是否已赞
  likePost: (id: number) => apiPost<boolean>(`/community/posts/${id}/like`),

  // ===== F-06 社区评论 =====

  /** 顶级评论分页（含每条预加载的前 3 条回复），sortBy: latest / hot / oldest */
  comments: (
    postId: number,
    params: { pageNum?: number; pageSize?: number; sortBy?: CommentSort } = {},
  ) => apiGet<CommentPageResult>(`/community/comments/post/${postId}`, params),

  /** 某条顶级评论下的全部回复（时间正序） */
  replies: (commentId: number, params: { pageNum?: number; pageSize?: number } = {}) =>
    apiGet<CommentPageResult>(`/community/comments/${commentId}/replies`, params),

  /** 发表评论或回复：parentId 为顶级评论 ID，replyToCommentId 为被回复的具体评论 */
  addComment: (data: {
    postId: number
    parentId?: number
    replyToCommentId?: number
    content: string
  }) => apiPost<CommentVO>('/community/comments', data),

  /** 编辑自己的评论 */
  updateComment: (id: number, data: { content: string }) =>
    apiPut<CommentVO>(`/community/comments/${id}`, data),

  /** 删除评论（作者本人或管理员） */
  deleteComment: (commentId: number) => apiDelete<void>(`/community/comments/${commentId}`),

  /** 评论点赞 / 取消点赞（幂等切换） */
  toggleCommentLike: (id: number) =>
    apiPost<CommentLikeResult>(`/community/comments/${id}/like`),
}
