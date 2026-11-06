package com.knowflow.interview.career.studio;

import com.knowflow.exception.BusinessException;
import com.knowflow.interview.service.career.CareerRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.knowflow.interview.controller.career.studio.ResumeStudioSkillController;
import com.knowflow.interview.service.career.studio.ResumeStudioSkillService;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 控制器必须同时保持 HTTP 与业务错误码语义。 */
class ResumeStudioSkillControllerTest {
    @Test
    void anonymousRegistryAccessIsUnauthorized() throws Exception {
        var controller = new ResumeStudioSkillController(new ResumeStudioSkillService(mock(CareerRecordService.class)));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/interview/career/studio/skills"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void preservesNotFoundAndConflictHttpCodes() {
        var controller = new ResumeStudioSkillController(null);
        var missing = controller.error(new BusinessException(404, "记录不存在"));
        var conflict = controller.error(new BusinessException(409, "请先确认简历版本"));
        org.junit.jupiter.api.Assertions.assertEquals(404, missing.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals(404, missing.getBody().getCode());
        org.junit.jupiter.api.Assertions.assertEquals(409, conflict.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals(409, conflict.getBody().getCode());
    }
}
