package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.UploadConfigProperties;
import com.knowflow.dto.GroupInviteDTO;
import com.knowflow.dto.StudyGroupCreateDTO;
import com.knowflow.entity.StudyGroup;
import com.knowflow.entity.StudyGroupMember;
import com.knowflow.entity.StudyGroupMessage;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-D5：{@link com.knowflow.service.impl.StudyGroupServiceImpl} 的 HTTP 错误语义收敛测试。
 *
 * <p><b>缺陷背景</b>：{@code StudyGroupServiceImpl} 原有 <b>22 处</b>裸 {@code RuntimeException}，
 * 全部是「用户请求本身触发、用户可理解」的业务错误，却经 {@code GlobalExceptionHandler} 兜底
 * 统一映射为 <b>HTTP 500「系统内部错误」</b>。用户自己的非法操作（重复加入、退出创建者、
 * 非成员访问）与真正服务端故障无法区分。</p>
 *
 * <p><b>收敛规则</b>（严格按资源语义，非机械替换）：
 * <ul>
 *   <li><b>404</b>：被请求的资源不存在（小组 / 成员 / 用户 / 消息）</li>
 *   <li><b>403</b>：已登录但无权（非成员、非创建者、非本人消息）</li>
 *   <li><b>400</b>：请求本身不成立 / 违反明确业务规则（重复加入、私有组直加、创建者退出、移除创建者）</li>
 * </ul>
 * 原有 message 文案<b>一字未改</b>；唯一一处「条件合并」（{@code updateMemberRole} 把
 * 「小组不存在」与「非创建者」并入同一分支）按真实语义拆为 404 + 403。</p>
 *
 * <p>本类的每个业务错误断言<b>同时</b>校验 HTTP status 与 {@code body.code}，
 * 专门防止「HTTP 200 + body.code 403」这类假修复。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库名：避免与共享 mem:knowflow 的 CREATE INDEX 冲突
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d5_studygroup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "app.upload.dir=${java.io.tmpdir}/knowflow-p0-sg-upload"
})
class StudyGroupErrorSemanticsTest {

    /** 本类专属用户，避免与共享库中的种子数据相互干扰。 */
    private static final Long USER_A = 97501L;
    private static final Long USER_B = 97502L;
    private static final Long USER_C = 97503L;
    private static final Long SYSTEM_ADMIN = 97504L;

    private static final Long MISSING_ID = 999999L;

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

    @Autowired
    private UploadConfigProperties uploadConfig;

    @BeforeEach
    void ensureTestUsers() {
        ensureUser(USER_A, "p1d5-user-a", "p1d5-a@example.com");
        ensureUser(USER_B, "p1d5-user-b", "p1d5-b@example.com");
        ensureUser(USER_C, "p1d5-user-c", "p1d5-c@example.com");
        ensureUser(SYSTEM_ADMIN, "p1d5-system-admin", "p1d5-admin@example.com", "ADMIN");
    }

    private void ensureUser(Long id, String username, String email) {
        ensureUser(id, username, email, "USER");
    }

    private void ensureUser(Long id, String username, String email, String role) {
        SysUser user = sysUserMapper.selectById(id);
        if (user != null) {
            user.setRole(role);
            sysUserMapper.updateById(user);
            return;
        }
        user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("x");
        user.setRole(role);
        sysUserMapper.insert(user);
    }

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1d5-user-" + userId, "USER");
    }

    private Long newGroup(Long ownerId, String name, String type) {
        StudyGroupCreateDTO dto = new StudyGroupCreateDTO();
        dto.setName(name);
        dto.setDescription("P1-D5 错误语义测试小组");
        dto.setType(type);
        return studyGroupService.createGroup(dto, ownerId).getId();
    }

    private Long newPublicGroup(Long ownerId, String name) {
        return newGroup(ownerId, name, "PUBLIC");
    }

    private Long newPrivateGroup(Long ownerId, String name) {
        return newGroup(ownerId, name, "PRIVATE");
    }

    /** 让 userId 加入 groupId（走 Service，绕开 HTTP，仅用于前置数据准备）。 */
    private void join(Long groupId, Long userId) {
        studyGroupService.joinGroup(groupId, userId);
    }

    /** 以 userId 身份发送一条群消息，返回 messageId。 */
    private Long sendMessage(Long groupId, Long userId) {
        return studyGroupService.sendMessage(
                buildMessageDto(groupId, "P1D5-" + userId + "-" + System.nanoTime()), userId).getId();
    }

    private com.knowflow.dto.GroupMessageSendDTO buildMessageDto(Long groupId, String content) {
        com.knowflow.dto.GroupMessageSendDTO dto = new com.knowflow.dto.GroupMessageSendDTO();
        dto.setGroupId(groupId);
        dto.setContent(content);
        return dto;
    }

    private StudyGroupMessage messageById(Long messageId) {
        return studyGroupMessageMapper.selectById(messageId);
    }

    private long memberRowCount(Long groupId) {
        return studyGroupMemberMapper.selectCount(
                new LambdaQueryWrapper<StudyGroupMember>().eq(StudyGroupMember::getGroupId, groupId));
    }

    private long messageRowCount(Long groupId) {
        return studyGroupMessageMapper.selectCount(
                new LambdaQueryWrapper<StudyGroupMessage>().eq(StudyGroupMessage::getGroupId, groupId));
    }

    /**
     * 断言「业务错误」四元组：HTTP status + {@code body.code} + {@code body.message} + 无 data 载荷。
     * <p>HTTP status 与 body.code 必须同时校验——只断言 code 无法发现「HTTP 200 + code 4xx」假修复。</p>
     */
    private void assertBusinessError(MockHttpServletRequestBuilder req,
                                     int expectedHttp, int expectedCode, String expectedMessage) throws Exception {
        mockMvc.perform(req)
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ============================================================
    // 一、401 未登录（与 P1-A3 principal 收敛保持一致，本轮零回归）
    // ============================================================

    @Test
    @DisplayName("P1D5-01: 匿名访问小组详情 → 401（不受本轮语义收敛影响）")
    void anonymousReturns401() throws Exception {
        assertBusinessError(get("/api/study-groups/1"), 401, 401, "未登录或登录已失效");
    }

    // ============================================================
    // 二、404：资源不存在
    // ============================================================

    @Test
    @DisplayName("P1D5-02 (404): 查询不存在的小组 → HTTP 404 + code 404（原为 500）")
    void groupNotFoundReturns404() throws Exception {
        assertBusinessError(get("/api/study-groups/" + MISSING_ID).header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");
    }

    @Test
    @DisplayName("P1D5-03 (404): 更新 / 删除 / 公告 / 关联计划 对不存在小组 → 一律 404，文案不变")
    void missingGroupAcrossWriteEndpointsReturns404() throws Exception {
        String dto = "{\"name\":\"p1d5-ghost\"}";

        assertBusinessError(put("/api/study-groups/" + MISSING_ID)
                        .header("Authorization", auth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON).content(dto),
                404, 404, "小组不存在");

        assertBusinessError(delete("/api/study-groups/" + MISSING_ID).header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");

        assertBusinessError(put("/api/study-groups/" + MISSING_ID + "/announcement")
                        .header("Authorization", auth(USER_A)).param("announcement", "ghost"),
                404, 404, "小组不存在");

        assertBusinessError(put("/api/study-groups/" + MISSING_ID + "/learning-plan")
                        .header("Authorization", auth(USER_A)).param("planId", "1"),
                404, 404, "小组不存在");

        assertBusinessError(post("/api/study-groups/" + MISSING_ID + "/join").header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");
    }

    @Test
    @DisplayName("P1D5-04 (404): 不存在的成员 → 404（移除 / 改角色）")
    void missingMemberReturns404() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-missing-member");
        Long ownerMemberId = currentMemberId(groupId, USER_A);

        assertBusinessError(delete("/api/study-groups/" + groupId + "/members/" + MISSING_ID)
                        .header("Authorization", auth(USER_A)),
                404, 404, "成员不存在");

        assertBusinessError(put("/api/study-groups/" + groupId + "/members/" + MISSING_ID + "/role")
                        .header("Authorization", auth(USER_A)).param("role", "ADMIN"),
                404, 404, "成员不存在");

        // 零副作用：创建者成员关系未被误删 / 误改
        StudyGroupMember owner = studyGroupMemberMapper.selectById(ownerMemberId);
        assertNotNull(owner);
        assertEquals("OWNER", owner.getRole());
    }

    @Test
    @DisplayName("P1D5-05 (404): 邀请不存在的用户 → 404（原为 500）")
    void inviteMissingUserReturns404() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-invite-missing");

        GroupInviteDTO dto = new GroupInviteDTO();
        dto.setGroupId(groupId);
        dto.setUserId(MISSING_ID);

        assertBusinessError(post("/api/study-groups/invite")
                        .header("Authorization", auth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)),
                404, 404, "用户不存在");
    }

    @Test
    @DisplayName("P1D5-06 (404): 撤回不存在的消息 → 404（原为 500），且无副作用")
    void recallMissingMessageReturns404() throws Exception {
        assertBusinessError(delete("/api/study-groups/messages/" + MISSING_ID)
                        .header("Authorization", auth(USER_A)),
                404, 404, "消息不存在");

        assertNull(messageById(MISSING_ID), "不存在的消息仍不存在");
    }

    @Test
    @DisplayName("P1D5-07 (404): 改角色时小组不存在 → 404「小组不存在」（原被并入 500 权限文案）")
    void updateMemberRoleMissingGroupReturns404() throws Exception {
        assertBusinessError(put("/api/study-groups/" + MISSING_ID + "/members/1/role")
                        .header("Authorization", auth(USER_A)).param("role", "ADMIN"),
                404, 404, "小组不存在");
    }

    @Test
    @DisplayName("P0-SG-01 (404): 不存在小组的已读 / 未读接口 → 404，不伪装成空数据")
    void readStateMissingGroupReturns404() throws Exception {
        assertBusinessError(post("/api/study-groups/" + MISSING_ID + "/read")
                        .header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");
        assertBusinessError(get("/api/study-groups/" + MISSING_ID + "/unread")
                        .header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");
    }

    // ============================================================
    // 三、403：已登录但无权限
    // ============================================================

    @Test
    @DisplayName("P1D5-08 (403): 非成员访问私有小组 → 403（原为 500），不泄漏小组内容")
    void nonMemberPrivateGroupReturns403() throws Exception {
        Long groupId = newPrivateGroup(USER_A, "p1d5-private");
        StudyGroup patch = new StudyGroup();
        patch.setId(groupId);
        patch.setName("p1d5-private-SECRET");
        studyGroupMapper.updateById(patch);

        String raw = mockMvc.perform(get("/api/study-groups/" + groupId).header("Authorization", auth(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该小组"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(raw.contains("SECRET"), "403 响应中不得出现小组名等受保护内容");
    }

    @Test
    @DisplayName("P1D5-09 (403): 非成员执行成员操作（列表 / 消息 / 发消息）→ 403，且零写入")
    void nonMemberMemberOperationsReturn403() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-member-gate");
        long messagesBefore = messageRowCount(groupId);

        assertBusinessError(get("/api/study-groups/" + groupId + "/members").header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");
        assertBusinessError(get("/api/study-groups/" + groupId + "/messages").header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");
        assertBusinessError(post("/api/study-groups/messages")
                        .header("Authorization", auth(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"content\":\"intruder\"}"),
                403, 403, "不是小组成员");

        // 零副作用：没有产生任何消息写入
        assertEquals(messagesBefore, messageRowCount(groupId), "非成员发消息必须零写入");
        assertNull(findMember(groupId, USER_B), "非成员不得因越权操作而成为成员");
    }

    @Test
    @DisplayName("P0-SG-02 (403): 非成员访问已读 / 未读接口 → 403，零泄漏、零写入")
    void nonMemberReadStateReturns403WithoutSideEffects() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-read-state-gate");
        sendMessage(groupId, USER_A);
        StudyGroupMember ownerBefore = findMember(groupId, USER_A);
        assertNotNull(ownerBefore);
        Long ownerCursorBefore = ownerBefore.getLastReadMessageId();
        long messagesBefore = messageRowCount(groupId);

        assertBusinessError(get("/api/study-groups/" + groupId + "/unread")
                        .header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");
        assertBusinessError(post("/api/study-groups/" + groupId + "/read")
                        .header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");

        assertNull(findMember(groupId, USER_B), "越权已读操作不得创建成员关系");
        assertEquals(messagesBefore, messageRowCount(groupId), "越权已读操作不得改变消息数据");
        assertEquals(ownerCursorBefore, findMember(groupId, USER_A).getLastReadMessageId(),
                "越权已读操作不得改变其他成员游标");
    }

    @Test
    @DisplayName("P0-SG-03 (200): 合法成员标记已读后游标推进，未读数归零")
    void memberMarkReadAdvancesCursorAndClearsUnreadCount() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-read-state-happy");
        join(groupId, USER_B);
        Long messageId = sendMessage(groupId, USER_A);

        mockMvc.perform(get("/api/study-groups/" + groupId + "/unread")
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(post("/api/study-groups/" + groupId + "/read")
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(messageId, findMember(groupId, USER_B).getLastReadMessageId());
        mockMvc.perform(get("/api/study-groups/" + groupId + "/unread")
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("P0-SG-04 (200): 空小组标记已读是无操作，不因 null 广播返回 500")
    void emptyGroupMarkReadIsSuccessfulNoOp() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-empty-read");

        mockMvc.perform(post("/api/study-groups/" + groupId + "/read")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(findMember(groupId, USER_A).getLastReadMessageId());
    }

    @Test
    @DisplayName("P0-SG-UP-01: 非成员上传 → 403，且磁盘零落盘")
    void nonMemberUploadReturns403WithoutWritingFile() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-upload-gate");
        long filesBefore = uploadedFileCount();
        MockMultipartFile file = new MockMultipartFile(
                "file", "safe.png", "image/png", new byte[]{1, 2, 3});

        assertBusinessError(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(file).header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");
        assertEquals(filesBefore, uploadedFileCount(), "鉴权失败必须发生在落盘之前");
    }

    @Test
    @DisplayName("P0-SG-UP-02: 不存在小组上传 → 404；空文件与主动内容 → 400，均零落盘")
    void invalidUploadsReturnExplicitErrorsWithoutWritingFiles() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-upload-validation");
        long filesBefore = uploadedFileCount();

        MockMultipartFile safe = new MockMultipartFile(
                "file", "safe.png", "image/png", new byte[]{1});
        assertBusinessError(multipart("/api/study-groups/{id}/upload", MISSING_ID)
                        .file(safe).header("Authorization", auth(USER_A)),
                404, 404, "小组不存在");

        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);
        assertBusinessError(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(empty).header("Authorization", auth(USER_A)),
                400, 400, "文件不能为空");

        MockMultipartFile html = new MockMultipartFile(
                "file", "attack.html", "text/html", "<script>alert(1)</script>".getBytes());
        assertBusinessError(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(html).header("Authorization", auth(USER_A)),
                400, 400, "不支持的文件类型");

        MockMultipartFile disguisedHtml = new MockMultipartFile(
                "file", "attack.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertBusinessError(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(disguisedHtml).header("Authorization", auth(USER_A)),
                400, 400, "文件内容与类型不匹配");

        MockMultipartFile mismatchedMime = new MockMultipartFile(
                "file", "note.txt", "application/octet-stream", "plain text".getBytes());
        assertBusinessError(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(mismatchedMime).header("Authorization", auth(USER_A)),
                400, 400, "不支持的文件类型");

        assertEquals(filesBefore, uploadedFileCount(), "校验失败不得产生文件");
    }

    @Test
    @DisplayName("P0-SG-UP-03: 合法成员上传安全图片 → 200，文件真实落盘")
    void memberCanUploadAllowedFile() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p0-sg-upload-happy");
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4});

        String body = mockMvc.perform(multipart("/api/study-groups/{id}/upload", groupId)
                        .file(file).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("note.png"))
                .andReturn().getResponse().getContentAsString();
        String fileUrl = objectMapper.readTree(body).path("data").path("fileUrl").asText();

        Path saved = Path.of(uploadConfig.getDir()).resolve(fileUrl.substring("/uploads/".length()));
        assertTrue(Files.exists(saved), "成功响应必须对应真实落盘文件");
        Files.deleteIfExists(saved);
    }

    @Test
    @DisplayName("P1D5-10 (403): 非成员尝试退出 → 403「不是小组成员」（同一文案与成员守卫一致）")
    void nonMemberLeaveReturns403() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-leave-gate");

        assertBusinessError(post("/api/study-groups/" + groupId + "/leave").header("Authorization", auth(USER_B)),
                403, 403, "不是小组成员");
    }

    @Test
    @DisplayName("P1D5-11 (403): 非创建者删除 / 修改 / 改公告 → 403，且小组与成员零变化")
    void nonOwnerAdminOperationsReturn403() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-admin-gate");
        join(groupId, USER_B);
        long membersBefore = memberRowCount(groupId);

        // 删除：文案来自 deleteGroup 的创建者校验
        assertBusinessError(delete("/api/study-groups/" + groupId).header("Authorization", auth(USER_B)),
                403, 403, "只有创建者可以删除小组");

        // 更新 / 改公告：文案来自 checkAdminPermission（B 是 MEMBER，非 OWNER/ADMIN）
        assertBusinessError(put("/api/study-groups/" + groupId)
                        .header("Authorization", auth(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p1d5-hijacked\"}"),
                403, 403, "没有管理权限");
        assertBusinessError(put("/api/study-groups/" + groupId + "/announcement")
                        .header("Authorization", auth(USER_B)).param("announcement", "hijacked"),
                403, 403, "没有管理权限");
        assertBusinessError(put("/api/study-groups/" + groupId + "/learning-plan")
                        .header("Authorization", auth(USER_B)).param("planId", "1"),
                403, 403, "没有管理权限");

        // 零副作用：小组仍存在、名称未改、成员数未变
        StudyGroup group = studyGroupMapper.selectById(groupId);
        assertNotNull(group, "越权删除后小组必须仍然存在");
        assertEquals("p1d5-admin-gate", group.getName());
        assertNull(group.getAnnouncement());
        assertEquals(membersBefore, memberRowCount(groupId));
    }

    @Test
    @DisplayName("P1D5-12 (403): 非创建者移除成员 / 改成员角色 → 403，成员关系零变化")
    void nonOwnerMemberManagementReturns403() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-member-mgmt-gate");
        join(groupId, USER_B);
        join(groupId, USER_C);
        Long memberC = currentMemberId(groupId, USER_C);

        // B（MEMBER）移除 C → 无管理权限
        assertBusinessError(delete("/api/study-groups/" + groupId + "/members/" + memberC)
                        .header("Authorization", auth(USER_B)),
                403, 403, "没有管理权限");

        // B（MEMBER）改 C 的角色 → 只有创建者
        assertBusinessError(put("/api/study-groups/" + groupId + "/members/" + memberC + "/role")
                        .header("Authorization", auth(USER_B)).param("role", "ADMIN"),
                403, 403, "只有创建者可以修改成员角色");

        // 零副作用：C 仍在且角色仍为 MEMBER
        StudyGroupMember c = studyGroupMemberMapper.selectById(memberC);
        assertNotNull(c, "越权移除后成员必须仍然存在");
        assertEquals("MEMBER", c.getRole(), "越权改角色必须零生效");
    }

    @Test
    @DisplayName("P1D5-13 (403): 撤回他人消息 → 403，且消息内容 / recalled / 更新时间零变化")
    void recallingOthersMessageReturns403WithZeroSideEffect() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-recall-gate");
        join(groupId, USER_B);

        Long messageId = sendMessage(groupId, USER_A);
        StudyGroupMessage before = messageById(messageId);
        assertNotNull(before);
        long messagesBefore = messageRowCount(groupId);

        assertBusinessError(delete("/api/study-groups/messages/" + messageId)
                        .header("Authorization", auth(USER_B)),
                403, 403, "只能撤回自己的消息");

        // 零副作用：逐字段快照全等
        StudyGroupMessage after = messageById(messageId);
        assertNotNull(after);
        assertEquals(0, after.getRecalled().intValue(), "越权撤回不得改变 recalled");
        assertEquals(before.getContent(), after.getContent(), "越权撤回不得改变内容");
        assertEquals(before.getSenderId(), after.getSenderId());
        assertEquals(before.getUpdateTime(), after.getUpdateTime(), "越权撤回不得触碰 update_time");
        assertEquals(messagesBefore, messageRowCount(groupId), "越权撤回不得产生额外行");
    }

    // ============================================================
    // 四、400：请求本身不合法 / 违反业务规则
    // ============================================================

    @Test
    @DisplayName("P1D5-14 (400): 直接加入私有小组 → 400（原为 500），且不成为成员")
    void joinPrivateGroupReturns400() throws Exception {
        Long groupId = newPrivateGroup(USER_A, "p1d5-private-join");

        assertBusinessError(post("/api/study-groups/" + groupId + "/join").header("Authorization", auth(USER_B)),
                400, 400, "私有小组需要邀请才能加入");

        assertNull(findMember(groupId, USER_B), "非法加入不得产生成员关系");
    }

    @Test
    @DisplayName("P1D5-15 (400): 重复加入公开小组 → 400「已是小组成员」，成员数不变")
    void joinTwiceReturns400() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-join-twice");
        join(groupId, USER_B);
        long membersBefore = memberRowCount(groupId);

        assertBusinessError(post("/api/study-groups/" + groupId + "/join").header("Authorization", auth(USER_B)),
                400, 400, "已是小组成员");

        assertEquals(membersBefore, memberRowCount(groupId), "重复加入不得重复插入成员行");
    }

    @Test
    @DisplayName("P1D5-16 (400): 创建者退出小组 → 400「创建者不能退出小组…」，仍是成员")
    void ownerLeaveReturns400() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-owner-leave");
        long membersBefore = memberRowCount(groupId);

        assertBusinessError(post("/api/study-groups/" + groupId + "/leave").header("Authorization", auth(USER_A)),
                400, 400, "创建者不能退出小组，请先转让或删除小组");

        assertNotNull(findMember(groupId, USER_A), "创建者退出被拒后仍是成员");
        assertEquals(membersBefore, memberRowCount(groupId));
    }

    @Test
    @DisplayName("P1D5-17 (400): 邀请已是成员的用户 → 400，成员行不重复")
    void inviteExistingMemberReturns400() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-invite-dup");
        join(groupId, USER_C);
        long membersBefore = memberRowCount(groupId);

        GroupInviteDTO dto = new GroupInviteDTO();
        dto.setGroupId(groupId);
        dto.setUserId(USER_C);

        assertBusinessError(post("/api/study-groups/invite")
                        .header("Authorization", auth(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)),
                400, 400, "用户已是小组成员");

        assertEquals(membersBefore, memberRowCount(groupId), "重复邀请不得重复插入成员行");
    }

    @Test
    @DisplayName("P1D5-18 (400): 移除创建者 → 400「不能移除创建者」，创建者成员关系保留")
    void removingOwnerReturns400() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-remove-owner");
        Long ownerMemberId = currentMemberId(groupId, USER_A);

        assertBusinessError(delete("/api/study-groups/" + groupId + "/members/" + ownerMemberId)
                        .header("Authorization", auth(USER_A)),
                400, 400, "不能移除创建者");

        assertNotNull(studyGroupMemberMapper.selectById(ownerMemberId), "创建者成员关系必须保留");
    }

    // ============================================================
    // 五、正常路径回归（证明收敛未改变成功语义）
    // ============================================================

    @Test
    @DisplayName("P1D5-19 (200): 合法成员正常读取（详情 / 成员 / 消息 / 未读）→ 200")
    void memberReadPathsStillWork() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-read");
        join(groupId, USER_B);

        for (String url : new String[]{"/api/study-groups/" + groupId,
                "/api/study-groups/" + groupId + "/members",
                "/api/study-groups/" + groupId + "/messages",
                "/api/study-groups/" + groupId + "/unread"}) {
            mockMvc.perform(get(url).header("Authorization", auth(USER_B)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    @DisplayName("P1D5-20 (200): 公开小组正常加入 / 退出 → 200，成员关系正确增删")
    void joinAndLeavePublicGroupStillWork() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-join");

        mockMvc.perform(post("/api/study-groups/" + groupId + "/join").header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("MEMBER", findMember(groupId, USER_B).getRole());

        mockMvc.perform(post("/api/study-groups/" + groupId + "/leave").header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertNull(findMember(groupId, USER_B));
    }

    @Test
    @DisplayName("P1D5-21 (200): 本人发送并撤回自己的消息 → 200，recalled 置 1")
    void sendAndRecallOwnMessageStillWork() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-recall");
        join(groupId, USER_B);

        String body = "{\"groupId\":" + groupId + ",\"content\":\"p1d5 own message\"}";
        String response = mockMvc.perform(post("/api/study-groups/messages")
                        .header("Authorization", auth(USER_B))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderId").value(USER_B))
                .andReturn().getResponse().getContentAsString();
        Long messageId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/study-groups/messages/" + messageId).header("Authorization", auth(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1, messageById(messageId).getRecalled().intValue(), "本人撤回应真实生效");
    }

    @Test
    @DisplayName("P1D5-22 (200): 创建者正常删除自己的小组 → 200，成员与消息级联清理")
    void ownerDeletingOwnGroupStillWorks() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-delete");
        join(groupId, USER_B);
        sendMessage(groupId, USER_B);

        mockMvc.perform(delete("/api/study-groups/" + groupId).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(studyGroupMapper.selectById(groupId));
        assertEquals(0, memberRowCount(groupId), "删除小组应级联清理成员");
        assertEquals(0, messageRowCount(groupId), "删除小组应级联清理消息");
    }

    @Test
    @DisplayName("P1D5-23 (200): 创建者正常移除非创建者成员 / 改角色 → 200")
    void ownerMemberManagementStillWorks() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-mgmt");
        join(groupId, USER_B);
        join(groupId, USER_C);
        Long memberC = currentMemberId(groupId, USER_C);

        mockMvc.perform(put("/api/study-groups/" + groupId + "/members/" + memberC + "/role")
                        .header("Authorization", auth(USER_A)).param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("ADMIN", studyGroupMemberMapper.selectById(memberC).getRole());

        mockMvc.perform(delete("/api/study-groups/" + groupId + "/members/" + memberC)
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertNull(studyGroupMemberMapper.selectById(memberC));

        // 成员数随管理操作同步
        assertEquals(2, memberRowCount(groupId));
    }

    @Test
    @DisplayName("P1D5-24 (200): 创建者正常更新公告 / 关联学习计划 → 200")
    void ownerAdminUpdatesStillWork() throws Exception {
        Long groupId = newPublicGroup(USER_A, "p1d5-happy-admin");

        mockMvc.perform(put("/api/study-groups/" + groupId + "/announcement")
                        .header("Authorization", auth(USER_A)).param("announcement", "P1D5 公告"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("P1D5 公告", studyGroupMapper.selectById(groupId).getAnnouncement());

        mockMvc.perform(put("/api/study-groups/" + groupId + "/learning-plan")
                        .header("Authorization", auth(USER_A)).param("planId", "4242"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(4242L, studyGroupMapper.selectById(groupId).getLearningPlanId());
    }

    @Test
    @DisplayName("ADMIN-CAP: 系统管理员可跨组管理，但无需伪造成小组成员")
    void systemAdminCanManageForeignGroupWithoutMembership() throws Exception {
        Long groupId = newPublicGroup(USER_A, "admin-cap-foreign-group");
        assertNull(findMember(groupId, SYSTEM_ADMIN));

        mockMvc.perform(put("/api/study-groups/" + groupId + "/announcement")
                        .header("Authorization", auth(SYSTEM_ADMIN)).param("announcement", "系统公告"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("系统公告", studyGroupMapper.selectById(groupId).getAnnouncement());
        assertNull(findMember(groupId, SYSTEM_ADMIN), "管理权限不应创建虚假的成员关系");

        mockMvc.perform(delete("/api/study-groups/" + groupId)
                        .header("Authorization", auth(SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertNull(studyGroupMapper.selectById(groupId));
    }

    // ============================================================
    // 辅助查询
    // ============================================================

    private StudyGroupMember findMember(Long groupId, Long userId) {
        return studyGroupMemberMapper.selectList(new LambdaQueryWrapper<StudyGroupMember>()
                        .eq(StudyGroupMember::getGroupId, groupId)
                        .eq(StudyGroupMember::getUserId, userId))
                .stream().findFirst().orElse(null);
    }

    private Long currentMemberId(Long groupId, Long userId) {
        StudyGroupMember member = findMember(groupId, userId);
        assertNotNull(member, "前置条件失败：用户 " + userId + " 应是小组 " + groupId + " 的成员");
        return member.getId();
    }

    private long uploadedFileCount() throws Exception {
        Path root = Path.of(uploadConfig.getDir());
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
