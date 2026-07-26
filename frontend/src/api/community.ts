import { apiGet, apiPost, apiDelete } from './request'
import type { PostVO, PostPageResult, CommentPageResult } from './types'

export const communityApi = {
  posts: (params: {
    category?: string
    sort?: string
    pageNum?: number
    pageSize?: number
  } = {}) => apiGet<PostPageResult>('/community/posts', params),

  postDetail: (id: number) => apiGet<PostVO>(`/community/posts/${id}`),

  createPost: (data: Partial<PostVO>) => apiPost<void>('/community/posts', data),

  // F-10：点赞/取消点赞幂等切换，返回当前是否已赞
  likePost: (id: number) => apiPost<boolean>(`/community/posts/${id}/like`),

  // 评论列表（按帖子分页）
  comments: (postId: number, params: { pageNum?: number; pageSize?: number } = {}) =>
    apiGet<CommentPageResult>(`/community/posts/${postId}/comments`, params),

  // 发表评论
  addComment: (postId: number, data: { content: string }) =>
    apiPost<void>(`/community/posts/${postId}/comments`, data),

  // 删除评论
  deleteComment: (commentId: number) => apiDelete<void>(`/community/comments/${commentId}`),
}
