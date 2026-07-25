package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.CommunityComment;
import com.knowflow.entity.CommunityPost;
import com.knowflow.vo.CommentVO;
import com.knowflow.vo.PostVO;

public interface CommunityService extends IService<CommunityPost> {

    IPage<PostVO> getPostPage(String category, String sort, Integer pageNum, Integer pageSize);

    PostVO getPostDetail(Long id);

    void createPost(CommunityPost post, Long userId);

    void deletePost(Long id, Long userId);

    void likePost(Long id, Long userId);

    IPage<CommentVO> getCommentPage(Long postId, Integer pageNum, Integer pageSize);

    void addComment(CommunityComment comment, Long userId);

    void deleteComment(Long id, Long userId);
}
