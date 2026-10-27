package com.knowflow.controller;

import com.knowflow.controller.admin.AdminCategoryController;
import com.knowflow.controller.admin.AdminKbMemberController;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.KbMember;
import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.CategoryService;
import com.knowflow.service.DocService;
import com.knowflow.service.KbMemberService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-D2：知识库「成员 / 角色 / 归属」越权点的 403 语义回归测试（共 8 处越权点）。
 *
 * <p>覆盖：{@code KbMemberServiceImpl:194/220}、{@code AdminKbMemberController:89/168}、
 * {@code AdminCategoryController:107/123/148/224}。</p>
 *
 * <p><b>与 {@code UserResourceOwnership403Test} 的关键差异</b>：这 8 处全部位于
 * {@code /api/admin/**}（{@code SecurityConfig} 的
 * {@code .requestMatchers("/api/admin/**").hasRole("ADMIN")}），而权限判定
 * {@code KbMemberServiceImpl#isOwner/canView/canEditDocs} 对 ADMIN 一律短路放行
 * （{@code isAdminUser()} 返回 true）。因此：</p>
 * <ul>
 *   <li><b>HTTP 层</b>：非管理员根本进不到这些判断（被角色门禁拦下，同为 403）；管理员永远通过。
 *       故这 8 个分支<b>在当前配置下无法被 HTTP 触发</b>。本测试用「显式设置非管理员 SecurityContext
 *       后直接调用 Controller/Service」逐点验证分支本体确实产出 403，<b>不用 HTTP 冒充</b>；</li>
 *   <li><b>同时用 HTTP 验证两件真实行为</b>：① 非管理员 → 403（角色门禁）；② <b>管理员对他人知识库
 *       仍可操作 → 200</b>（§十一 要求：403 收敛不得把管理员正常权限误判成 403）。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 与 UserResourceOwnership403Test 使用完全一致的属性值 → 复用同一个 Spring 上下文与同一个 H2 库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class KnowledgeBaseMembership403Test {

    private static final String CATEGORY_NAME = "P1D2-知识库";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private KbMemberService kbMemberService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private DocService docService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private AdminKbMemberController adminKbMemberController;

    @Autowired
    private AdminCategoryController adminCategoryController;

    /** 知识库 Owner。 */
    private Long ownerId;
    /** 知识库成员（EDITOR）：有权限但不是 Owner。 */
    private Long editorId;
    /** 与知识库毫无关系的普通用户。 */
    private Long outsiderId;
    /** 系统管理员。 */
    private Long adminId;

    /** 被测知识库（ownerId 所有）。 */
    private Long categoryId;
    /** editorId 在该知识库中的成员记录 id。 */
    private Long editorMemberId;

    // ============================ 脚手架 ============================

    /**
     * 同一 Spring 上下文 / 同一个 H2 库被本类全部用例共享，且 JUnit 不保证方法执行顺序，
     * 因此每个用例前把「成员关系」与「知识库名称」还原到基准态，避免用例互相污染。
     */
    @BeforeEach
    void setUp() throws Exception {
        if (categoryId == null) {
            initFixture();
            return;
        }
        KbMember editor = kbMemberService.getMember(categoryId, editorId);
        if (editor == null) {
            editorMemberId = kbMemberService.addMember(categoryId, editorId, KbMember.ROLE_EDITOR, null).getId();
        } else {
            editor.setRole(KbMember.ROLE_EDITOR);
            kbMemberService.updateById(editor);
            editorMemberId = editor.getId();
        }
        KbMember outsider = kbMemberService.getMember(categoryId, outsiderId);
        if (outsider != null) {
            Long outsiderMemberId = outsider.getId();
            as(adminId, "ADMIN", () -> kbMemberService.removeMember(outsiderMemberId, adminId));
        }
        DocCategory restore = new DocCategory();
        restore.setId(categoryId);
        restore.setName(CATEGORY_NAME);
        categoryService.updateById(restore);
    }

    private void initFixture() {
        ownerId = insertUser("p1d2-owner-" + System.nanoTime(), "USER");
        editorId = insertUser("p1d2-editor-" + System.nanoTime(), "USER");
        outsiderId = insertUser("p1d2-outsider-" + System.nanoTime(), "USER");
        adminId = insertUser("p1d2-admin-" + System.nanoTime(), "ADMIN");

        DocCategory category = new DocCategory();
        category.setName(CATEGORY_NAME);
        category.setParentId(0L);
        category.setOwnerId(ownerId);
        category.setStatus(1);
        categoryService.save(category);
        categoryId = category.getId();

        kbMemberService.addMember(categoryId, ownerId, KbMember.ROLE_OWNER, null);
        editorMemberId = kbMemberService.addMember(categoryId, editorId, KbMember.ROLE_EDITOR, null).getId();
    }

    private Long insertUser(String username, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmail(username + "@knowflow.test");
        user.setPassword("");
        user.setNickname(username);
        user.setRole(role);
        sysUserMapper.insert(user);
        return user.getId();
    }

    /** 在指定身份的 SecurityContext 下执行动作；结束后清理上下文，避免污染其它用例。 */
    private void as(Long userId, String role, ThrowingAction action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 断言动作抛出 403 业务异常，并返回该异常以便继续校验文案。 */
    private BusinessException assert403(ThrowingAction action) throws Exception {
        try {
            action.run();
        } catch (BusinessException e) {
            assertEquals(Integer.valueOf(403), e.getCode(), "业务码必须为 403，实际=" + e.getCode());
            return e;
        }
        throw new AssertionError("应抛出 BusinessException(403)，但未抛出");
    }

    /** 在指定身份的 SecurityContext 下执行有返回值的动作（用于把 assert403 的结果带出作用域）。 */
    private <T> T asR(Long userId, String role, ThrowingSupplier<T> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private String token(Long userId, String role) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1d2-" + userId, role);
    }

    // ============================================================
    // 一、KbMemberServiceImpl:194 / 220（成员角色与移除）
    // ============================================================

    @Test
    @DisplayName("P1-D2-17 核心: 非 Owner 修改成员角色 → code 403（原为 500）且角色未被改写")
    void nonOwnerCannotChangeRole() throws Exception {
        BusinessException e = asR(editorId, "USER",
                () -> assert403(() -> kbMemberService.changeRole(editorMemberId, KbMember.ROLE_OWNER, editorId)));
        assertEquals("无权修改成员角色", e.getMessage(), "文案必须保持原业务含义");

        assertEquals(KbMember.ROLE_EDITOR, kbMemberService.getById(editorMemberId).getRole(),
                "越权失败后角色必须保持 EDITOR");
    }

    @Test
    @DisplayName("P1-D2-18 核心: 非 Owner 移除成员 → code 403 且成员仍有效")
    void nonOwnerCannotRemoveMember() throws Exception {
        BusinessException e = asR(editorId, "USER",
                () -> assert403(() -> kbMemberService.removeMember(editorMemberId, editorId)));
        assertEquals("无权移除成员", e.getMessage());

        KbMember still = kbMemberService.getMember(categoryId, editorId);
        assertNotNull(still, "越权失败后成员记录必须仍然有效");
        assertEquals(Integer.valueOf(KbMember.STATUS_ACTIVE), still.getStatus(), "成员状态不得被改写");
    }

    @Test
    @DisplayName("P1-D2-19: Owner 修改角色 / 移除成员 → 正常生效（能力未被破坏）")
    void ownerCanManageMembers() throws Exception {
        as(ownerId, "USER", () -> kbMemberService.changeRole(editorMemberId, KbMember.ROLE_READER, ownerId));
        assertEquals(KbMember.ROLE_READER, kbMemberService.getById(editorMemberId).getRole(),
                "Owner 的角色变更必须真实生效");

        as(ownerId, "USER", () -> kbMemberService.removeMember(editorMemberId, ownerId));
        assertNull(kbMemberService.getMember(categoryId, editorId),
                "Owner 的移除必须真实生效（成员记录不再有效）");
    }

    @Test
    @DisplayName("P1-D2-20 §十一回归: 系统 ADMIN 对他人知识库仍可管理成员 → 不被 403 误伤")
    void adminStillBypassesOwnership() throws Exception {
        KbMember editor = kbMemberService.getMember(categoryId, editorId);
        assertNotNull(editor, "基准态必须存在 EDITOR 成员");

        // ADMIN 不是该知识库成员，却必须被放行
        as(adminId, "ADMIN", () -> kbMemberService.changeRole(editor.getId(), KbMember.ROLE_READER, adminId));
        assertEquals(KbMember.ROLE_READER, kbMemberService.getById(editor.getId()).getRole(),
                "ADMIN 越库管理成员必须仍然放行");

        as(adminId, "ADMIN", () -> kbMemberService.removeMember(editor.getId(), adminId));
        assertNull(kbMemberService.getMember(categoryId, editorId),
                "ADMIN 越库移除成员必须仍然放行");
    }

    // ============================================================
    // 二、AdminKbMemberController:89 / 168（分支本体）
    // ============================================================

    @Test
    @DisplayName("P1-D2-21 核心: 非成员查看知识库成员列表 → code 403（AdminKbMemberController:89）")
    void nonMemberCannotListMembers() throws Exception {
        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminKbMemberController.listByCategory(categoryId)));
        assertEquals("无权限查看该知识库成员", e.getMessage());
    }

    @Test
    @DisplayName("P1-D2-22 核心: 非 Owner 向知识库添加成员 → code 403（AdminKbMemberController:168）")
    void nonOwnerCannotAddMember() throws Exception {
        AdminKbMemberController.AddMemberReq req = new AdminKbMemberController.AddMemberReq();
        req.setCategoryId(categoryId);
        req.setUserId(outsiderId);
        req.setRole(KbMember.ROLE_READER);

        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminKbMemberController.add(req)));
        assertEquals("无权限管理该知识库成员", e.getMessage());

        assertNull(kbMemberService.getMember(categoryId, outsiderId),
                "越权失败后不得产生成员记录");
    }

    // ============================================================
    // 三、AdminCategoryController:107 / 123 / 148 / 224（分支本体）
    // ============================================================

    @Test
    @DisplayName("P1-D2-23 核心: 非 Owner 修改知识库 → code 403（:107）且名称未被改写")
    void nonOwnerCannotUpdateCategory() throws Exception {
        DocCategory payload = new DocCategory();
        payload.setName("越权改名");

        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminCategoryController.update(categoryId, payload)));
        assertEquals("无权修改该知识库", e.getMessage());

        assertEquals(CATEGORY_NAME, categoryService.getById(categoryId).getName(), "越权修改不得生效");
    }

    @Test
    @DisplayName("P1-D2-24 核心: 非 Owner 删除知识库 → code 403（:123）且知识库仍在")
    void nonOwnerCannotDeleteCategory() throws Exception {
        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminCategoryController.delete(categoryId)));
        assertEquals("无权删除该知识库", e.getMessage());

        assertNotNull(categoryService.getById(categoryId), "越权删除不得生效");
    }

    @Test
    @DisplayName("P1-D2-25 核心: 无编辑权限向知识库导入文档 → code 403（:148）且未产生文档")
    void nonEditorCannotImportDocs() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "p1d2.md", "text/markdown",
                "P1D2 越权导入内容".getBytes(StandardCharsets.UTF_8));
        int docCountBefore = docService.listByCategory(categoryId).size();

        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminCategoryController.importDocs(categoryId, file)));
        assertEquals("无权向该知识库导入文档", e.getMessage());

        assertEquals(docCountBefore, docService.listByCategory(categoryId).size(),
                "越权导入不得产生任何文档");
    }

    @Test
    @DisplayName("P1-D2-26 核心: 非成员导出知识库 → code 403（:224）")
    void nonMemberCannotExportCategory() throws Exception {
        BusinessException e = asR(outsiderId, "USER",
                () -> assert403(() -> adminCategoryController.export(categoryId, "zip")));
        assertEquals("无权导出该知识库", e.getMessage());
    }

    @Test
    @DisplayName("P1-D2-27: 权限判定前置条件成立（非成员无任何权限位；EDITOR 无 Owner 位）")
    void permissionBitsAreCorrect() throws Exception {
        as(outsiderId, "USER", () -> {
            assertFalse(kbMemberService.isOwner(categoryId, outsiderId), "非成员不得是 Owner");
            assertFalse(kbMemberService.canView(categoryId, outsiderId), "非成员不得可读");
            assertFalse(kbMemberService.canEditDocs(categoryId, outsiderId), "非成员不得可编辑");
        });
        as(editorId, "USER", () -> {
            assertFalse(kbMemberService.isOwner(categoryId, editorId), "EDITOR 不得是 Owner");
            assertTrue(kbMemberService.canView(categoryId, editorId), "EDITOR 必须可读");
            assertTrue(kbMemberService.canEditDocs(categoryId, editorId), "EDITOR 必须可编辑文档");
        });
        as(ownerId, "USER", () -> {
            assertTrue(kbMemberService.isOwner(categoryId, ownerId), "Owner 必须具备 Owner 位");
            assertTrue(kbMemberService.canEditDocs(categoryId, ownerId), "Owner 必须可编辑文档");
        });
    }

    // ============================================================
    // 四、HTTP 层：角色门禁（非管理员 403）+ 管理员回归（200）
    // ============================================================

    @Test
    @DisplayName("P1-D2-28 核心: 非管理员访问 /api/admin/** → HTTP 403 + code 403，且知识库未被改写")
    void nonAdminGot403FromRoleGate() throws Exception {
        mockMvc.perform(put("/api/admin/categories/" + categoryId)
                        .header("Authorization", token(outsiderId, "USER"))
                        .contentType("application/json")
                        .content("{\"name\":\"越权改名\",\"parentId\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/kb-members/category/" + categoryId)
                        .header("Authorization", token(outsiderId, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/admin/categories/" + categoryId)
                        .header("Authorization", token(outsiderId, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(CATEGORY_NAME, categoryService.getById(categoryId).getName(),
                "被门禁拦下的请求不得产生副作用");

        // 匿名 → 401
        mockMvc.perform(put("/api/admin/categories/" + categoryId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("P1-D2-29 §十一回归: ADMIN 修改他人知识库 → HTTP 200（403 收敛未误伤管理员）")
    void adminCanUpdateForeignCategory() throws Exception {
        mockMvc.perform(put("/api/admin/categories/" + categoryId)
                        .header("Authorization", token(adminId, "ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"P1D2-由管理员改名\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("P1D2-由管理员改名", categoryService.getById(categoryId).getName(),
                "管理员对他人知识库的修改必须真实生效");
    }

    @Test
    @DisplayName("P1-D2-30 §十一回归: ADMIN 查看他人知识库成员 / 添加成员 → HTTP 200")
    void adminCanManageForeignKbMembers() throws Exception {
        mockMvc.perform(get("/api/admin/kb-members/category/" + categoryId)
                        .header("Authorization", token(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/admin/kb-members")
                        .header("Authorization", token(adminId, "ADMIN"))
                        .contentType("application/json")
                        .content("{\"categoryId\":" + categoryId + ",\"userId\":" + outsiderId
                                + ",\"role\":\"READER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNotNull(kbMemberService.getMember(categoryId, outsiderId), "管理员添加成员必须真实生效");
    }

    /**
     * 回归：知识库不存在时 {@code AdminCategoryController#update} 仍是既有的「知识库不存在」语义。
     * <p>该分支使用单参 {@code BusinessException("知识库不存在")}（HTTP 200 + code 500），
     * 属本轮范围外的既有缺陷，本用例只锁定「403 收敛<b>没有</b>把『不存在』误改成 403」——
     * 即不存在的资源不得被判成无权限。</p>
     */
    @Test
    @DisplayName("P1-D2-31 回归: 知识库不存在时不被改判为 403（语义必须与「无权限」区分）")
    void notFoundIsNotRewrittenTo403() throws Exception {
        DocCategory payload = new DocCategory();
        payload.setName("不存在");

        try {
            as(outsiderId, "USER", () -> adminCategoryController.update(999999L, payload));
            throw new AssertionError("知识库不存在时必须失败");
        } catch (BusinessException ex) {
            assertEquals("知识库不存在", ex.getMessage());
            assertFalse(Integer.valueOf(403).equals(ex.getCode()),
                    "「资源不存在」不得被改判为 403（语义不同）");
        }
    }
}
