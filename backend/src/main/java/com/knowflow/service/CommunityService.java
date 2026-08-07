package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.entity.CommunityPost;
import com.knowflow.vo.PostVO;

/**
 * 社区帖子业务服务。
 *
 * <p>评论相关能力已收敛到 {@link CommunityCommentService}，本接口只负责帖子本身。</p>
 */
public interface CommunityService extends IService<CommunityPost> {

    IPage<PostVO> getPostPage(String category, String sort, Integer pageNum, Integer pageSize);

    PostVO getPostDetail(Long id);

    void createPost(CommunityPost post, Long userId);

    void deletePost(Long id, Long userId);

    /**
     * F-10：点赞切换（幂等）。已赞则取消并返回 false，未赞则点赞并返回 true。
     */
    boolean likePost(Long id, Long userId);
}
