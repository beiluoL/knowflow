package com.knowflow.interview.service;

import com.knowflow.common.PageResult;
import com.knowflow.interview.vo.InterviewVos.AnswerRequest;
import com.knowflow.interview.vo.InterviewVos.SessionVO;
import com.knowflow.interview.vo.InterviewVos.StartRequest;
import com.knowflow.interview.vo.InterviewVos.TrackVO;

import java.util.List;

/**
 * 模拟面试服务（Phase 3H）。
 *
 * <p>基础模式（BASIC）不依赖任何 AI：预置题 + 追问树 + 手工作答 + 自评量表 + 服务端计时与持久化。
 * 所有方法必须由服务端按认证 userId 归属校验，不信任请求中的用户身份。
 */
public interface MockInterviewService {

    /** 面试方向目录（白名单即题库本身）。 */
    List<TrackVO> listTracks();

    /** 准备页只读题名目录，不返回参考或评分信息。 */
    List<java.util.Map<String,String>> listQuestions(Long actor, String track);

    /** 开始一场基础模式面试：题目整体快照入库，题库后续变更不影响本场。 */
    SessionVO start(Long userId, StartRequest req);

    /** 读取本人会话详情（题目快照 + 已保存作答）；他人/不存在一律 404。 */
    SessionVO getOwn(Long userId, Long sessionId);

    /** 保存/更新某一题的作答（幂等：同题重复保存为覆盖更新，不产生重复记录）。 */
    SessionVO saveAnswer(Long userId, Long sessionId, AnswerRequest req);

    /** 结束面试：全部题目作答且自评完成才允许，服务端计算均分与用时。 */
    SessionVO finish(Long userId, Long sessionId);

    /** 本人历史会话（分页，按开始时间倒序）。 */
    PageResult<?> mySessions(Long userId, int page, int pageSize);
}
