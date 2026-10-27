package com.knowflow.interview.bridge;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class InterviewBridgeHttpTest {
    MockMvc http; InterviewLearningBridge bridge;
    @BeforeEach void setup(){bridge=mock(InterviewLearningBridge.class);http=MockMvcBuilders.standaloneSetup(new InterviewLearningBridgeController(bridge)).build();}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void anonymousAndOwnedBoundaryUseRealHttpAndBodyCodes() throws Exception {
        http.perform(get("/api/interview/v2/sessions/1/remediation")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,"unused",List.of()));
        for(int code:List.of(400,404,409,500)) {
            doThrow(new BusinessException(code,"blocked")).when(bridge).preview(7L,1L);
            http.perform(get("/api/interview/v2/sessions/1/remediation")).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code));
        }
    }
    @Test void confirmUsesAuthenticatedActorAndExactPayload() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L,"unused",List.of()));
        var req=new InterviewLearningBridge.ConfirmRequest("req_1",List.of(3L));
        when(bridge.confirm(7L,1L,req)).thenReturn(List.of(new InterviewLearningBridge.Confirmed(3L,99L)));
        http.perform(post("/api/interview/v2/sessions/1/remediation/confirm").contentType("application/json").content("{\"requestId\":\"req_1\",\"knowledgeIds\":[3]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].taskId").value(99));
        verify(bridge).confirm(7L,1L,req);
    }
}
