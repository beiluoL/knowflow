package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.GroupInviteDTO;
import com.knowflow.dto.StudyGroupCreateDTO;
import com.knowflow.entity.StudyGroup;
import com.knowflow.entity.StudyGroupMember;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.StudyGroupMapper;
import com.knowflow.mapper.StudyGroupMemberMapper;
import com.knowflow.mapper.StudyGroupMessageMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.StudyGroupService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-A 第三批：{@link StudyGroupController} 的 principal 安全收敛测试。
 *
 * <p>缺陷背景：{@code StudyGroupController} 原先有 <b>19 处</b>内联
 * {@code (Long) authentication.getPrincipal()}。对已登录请求该强转成立，
 * 但匿名 principal 是字符串 {@code "anonymousUser"}（配置未 {@code anonymous().disable()}），
 * 强转会抛 {@code ClassCastException} → 落进 {@code GlobalExceptionHandler} 兜底 →
 * <b>HTTP 500「系统内部错误」</b>。由于 {@code /api/study-groups/**} 在
 * {@code SecurityConfig} 中落入 {@code .anyRequest().authenticated()}，正确语义应为
 * <b>401 未登录</b>。</p>
 *
 * <p>修复方式：收敛到既有 {@link com.knowflow.common.SecurityUtils}，
 * 复用既有 {@code BusinessException(401, ...)} 业务码机制，不新建鉴权机制。
 * 实现模式与 P1-A 第一批 {@link ChatPrincipalAuthTest}、第二批 {@link LearningPrincipalAuthTest}
 * 完全一致。</p>
 *
 * <p><b>关于既有业务异常语义的重要说明</b>：
 * {@link StudyGroupServiceImpl} 当前对「小组不存在 / 无权访问 / 无管理权限」等场景抛出
 * {@code RuntimeException}，经 {@code GlobalExceptionHandler#handleException} 映射为
 * <b>HTTP 500 + body.code 500</b>，并非 404/403。本轮严格按 scope 只收敛 principal，
 * 未改动 Service 业务异常。因此以下「不存在 / 越权」回归测试断言的是<b>当前真实语义</b>，
 * 用于证明 principal 收敛没有顺手改变这些业务语义。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库名：避免与共享 mem:knowflow 的 CREATE INDEX 冲突
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1a_studygroup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class StudyGroupPrincipalAuthTest {

    /** 本类专属用户，避免与共享库中的种子数据相互干扰。 */
    private static final Long USER_A = 97401L;
    private static final Long USER_B = 97402L;
    private static final Long USER_C = 97403L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StudyGroupService studyGroupService;

    @Autowired
    private StudyGroupMapper studyGroupMapper;

    @Autowired
    private StudyGroupMemberMapper studyGroupMemberMapper;

    @Autowired
    private StudyGroupMessageMapper studyGroupMessageMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** 为每个测试用例确保存在互不冲突的测试用户（H2 独立库中无种子用户）。 */
    @BeforeEach
    void ensureTestUsers() {
        ensureUser(USER_A, "p1a3-user-a", "p1a3-a@example.com");
        ensureUser(USER_B, "p1a3-user-b", "p1a3-b@example.com");
        ensureUser(USER_C, "p1a3-user-c", "p1a3-c@example.com");
    }

    private void ensureUser(Long id, String username, String email) {
        SysUser user = sysUserMapper.selectById(id);
        if (user != null) {
            return;
        }
        user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("x");
        user.setRole("USER");
        sysUserMapper.insert(user);
    }

    private String tokenA() {
        return jwtUtils.generateToken(USER_A, "p1a-studygroup-a", "USER");
    }

    private String tokenB() {
        return jwtUtils.generateToken(USER_B, "p1a-studygroup-b", "USER");
    }

    /** 创建一个公开小组，所有者为 USER_A。 */
    private Long newPublicGroupForA(String name) {
        StudyGroupCreateDTO dto = new StudyGroupCreateDTO();
        dto.setName(name);
        dto.setDescription("P1-A 第三批测试小组");
        dto.setType("PUBLIC");
        return studyGroupService.createGroup(dto, USER_A).getId();
    }

    /** 创建一个私有小组，所有者为 USER_A。 */
    private Long newPrivateGroupForA(String name) {
        StudyGroupCreateDTO dto = new StudyGroupCreateDTO();
        dto.setName(name);
        dto.setDescription("P1-A 第三批私有测试小组");
        dto.setType("PRIVATE");
        return studyGroupService.createGroup(dto, USER_A).getId();
    }

    // ============================================================
    // 一、身份收敛核心：匿名 / 无效 token → 401（原为 500）
    // ============================================================

    @Test
    @DisplayName("P1-A3-1: 匿名访问 /api/study-groups/my → 401（原为 500）")
    void anonymousMyGroupsReturns401() throws Exception {
        mockMvc.perform(get("/api/study-groups/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    @Test
    @DisplayName("P1-A3-2: 无效 token 访问 /api/study-groups/my → 401")
    void invalidTokenMyGroupsReturns401() throws Exception {
        mockMvc.perform(get("/api/study-groups/my")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
    }

    @Test
    @DisplayName("P1-A3-3: 匿名访问写接口 /api/study-groups → 401（不会被表述为 500）")
    void anonymousCreateGroupReturns401() throws Exception {
        String body = "{\"name\":\"P1A3-anonymous-create\"}";
        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 二、有效 token 正常：身份绑定 + 读接口回归
    // ============================================================

    @Test
    @DisplayName("P1-A3-4: 有效 token 创建小组 → 200，owner 为 token 中的用户")
    void validTokenCreateGroupBindsOwner() throws Exception {
        String body = "{\"name\":\"P1A3-owner-binding\",\"type\":\"PUBLIC\"}";

        String response = mockMvc.perform(post("/api/study-groups")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ownerId").value(USER_A))
                .andExpect(jsonPath("$.data.userRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString();

        Long groupId = objectMapper.readTree(response).path("data").path("id").asLong();
        StudyGroup group = studyGroupMapper.selectById(groupId);
        assertNotNull(group);
        assertEquals(USER_A, group.getOwnerId());
    }

    @Test
    @DisplayName("P1-A3-5: 有效 token 读接口行为不变")
    void validTokenReadEndpointsWork() throws Exception {
        Long groupId = newPublicGroupForA("P1A3-read-regression");

        // 我加入的小组
        mockMvc.perform(get("/api/study-groups/my")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 推荐小组
        mockMvc.perform(get("/api/study-groups/recommend")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 小组详情
        mockMvc.perform(get("/api/study-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(groupId));

        // 成员列表
        mockMvc.perform(get("/api/study-groups/" + groupId + "/members")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ============================================================
    // 三、请求体 userId 不能覆盖当前登录用户身份
    // ============================================================

    @Test
    @DisplayName("P1-A3-6: 请求体中的 userId 被解释为被邀请人，不覆盖当前登录用户")
    void requestBodyUserIdDoesNotOverrideCurrentUser() throws Exception {
        Long groupId = newPublicGroupForA("P1A3-invite-userid");

        // A 邀请 C：dto.userId = C，当前登录用户仍为 A
        GroupInviteDTO dto = new GroupInviteDTO();
        dto.setGroupId(groupId);
        dto.setUserId(USER_C);
        dto.setRole("MEMBER");

        mockMvc.perform(post("/api/study-groups/invite")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 被邀请人是 C，邀请人是 A
        StudyGroupMember member = studyGroupMemberMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyGroupMember>()
                                .eq(StudyGroupMember::getGroupId, groupId)
                                .eq(StudyGroupMember::getUserId, USER_C))
                .stream().findFirst().orElse(null);
        assertNotNull(member);
        assertEquals(USER_C, member.getUserId());
        assertEquals(USER_A, member.getInvitedBy());
    }

    // ============================================================
    // 四、用户隔离：B 不能越权操作 A 的资源
    // ============================================================

    @Test
    @DisplayName("P1-A3-7: 用户 B 不能修改用户 A 的小组（P1-D5 收敛为 403）")
    void userBCannotModifyUserAGroup() throws Exception {
        Long groupId = newPublicGroupForA("P1A3-update-isolation");

        String body = "{\"name\":\"P1A3-hijacked\"}";
        // P1-D5：checkAdminPermission 抛 BusinessException(403, "没有管理权限")
        mockMvc.perform(put("/api/study-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("没有管理权限"));

        // 零副作用：小组名称未被改写
        StudyGroup group = studyGroupMapper.selectById(groupId);
        assertEquals("P1A3-update-isolation", group.getName());
    }

    @Test
    @DisplayName("P1-A3-8: 用户 B 不能删除用户 A 的小组（P1-D5 收敛为 403）")
    void userBCannotDeleteUserAGroup() throws Exception {
        Long groupId = newPublicGroupForA("P1A3-delete-isolation");

        // P1-D5：deleteGroup 的创建者校验抛 BusinessException(403, "只有创建者可以删除小组")
        mockMvc.perform(delete("/api/study-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("只有创建者可以删除小组"));

        // 零副作用：小组仍在
        assertNotNull(studyGroupMapper.selectById(groupId));
    }

    // ============================================================
    // 五、既有业务异常语义保持：不存在 / 私有组非成员
    // ============================================================

    @Test
    @DisplayName("P1-A3-9: 小组不存在（P1-D5 收敛为 404）")
    void groupNotExistsKeepsExistingSemantics() throws Exception {
        // P1-D5：抛出 BusinessException(404, "小组不存在")
        mockMvc.perform(get("/api/study-groups/999999")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("小组不存在"));
    }

    @Test
    @DisplayName("P1-A3-10: 非成员访问私有小组（P1-D5 收敛为 403）")
    void nonMemberPrivateGroupKeepsExistingSemantics() throws Exception {
        Long groupId = newPrivateGroupForA("P1A3-private-isolation");

        // P1-D5：抛出 BusinessException(403, "无权访问该小组")
        mockMvc.perform(get("/api/study-groups/" + groupId)
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该小组"));
    }

    // ============================================================
    // 六、正常成员操作回归
    // ============================================================

    @Test
    @DisplayName("P1-A3-11: 成员操作行为不变：加入 / 发送消息 / 离开")
    void memberOperationsStillWork() throws Exception {
        Long groupId = newPublicGroupForA("P1A3-member-flow");

        // B 加入公开小组
        mockMvc.perform(post("/api/study-groups/" + groupId + "/join")
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        StudyGroupMember membership = studyGroupMemberMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyGroupMember>()
                                .eq(StudyGroupMember::getGroupId, groupId)
                                .eq(StudyGroupMember::getUserId, USER_B))
                .stream().findFirst().orElse(null);
        assertNotNull(membership);
        assertEquals("MEMBER", membership.getRole());

        // B 发送消息
        String messageBody = "{\"groupId\":" + groupId + ",\"content\":\"hello from B\"}";
        mockMvc.perform(post("/api/study-groups/messages")
                        .header("Authorization", "Bearer " + tokenB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderId").value(USER_B));

        // B 离开小组
        mockMvc.perform(post("/api/study-groups/" + groupId + "/leave")
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 离开后成员关系不存在
        assertTrue(studyGroupMemberMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyGroupMember>()
                                .eq(StudyGroupMember::getGroupId, groupId)
                                .eq(StudyGroupMember::getUserId, USER_B))
                .isEmpty());
    }
}
