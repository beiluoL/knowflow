package com.knowflow.identity.contract;

import java.util.Collection;
import java.util.List;

/** 仅查询公开展示身份；调用方仍负责资源授权。 */
public interface MemberDisplayQuery {
    /** 账号不存在或已逻辑删除时返回 null。 */
    MemberDisplay find(Long userId);
    /** 解析已有账号，不暴露凭据或私有账号属性。 */
    List<MemberDisplay> findAll(Collection<Long> userIds);

    /** 会话和发送者标签使用的公开身份字段。 */
    record MemberDisplay(Long id, String username, String nickname, String avatar) {}
}
