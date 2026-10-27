package com.knowflow.controller;

import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.config.AiConfig;
import com.knowflow.config.AiProviderRegistry;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.UserAiConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiConfigTestGateTest {
    @Mock UserAiConfigMapper mapper;
    @Mock AiConfig config;
    @Mock AiProviderRegistry registry;
    @Mock AiCapabilityProperties capabilities;
    @InjectMocks AiConfigController controller;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void login() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(42L,null,List.of())); }
    @Test void disabledTestRejectsBeforeCredentialsOrProviderLookup() {
        login();
        BusinessException error=assertThrows(BusinessException.class,()->controller.testConnection(Map.of(),null));
        assertEquals(422,error.getCode());
        verifyNoInteractions(mapper,registry);
    }
    @Test void anonymousStillReturns401BeforeCapability() {
        BusinessException error=assertThrows(BusinessException.class,()->controller.testConnection(Map.of(),null));
        assertEquals(401,error.getCode());
        verifyNoInteractions(capabilities,mapper,registry);
    }
    @Test void enabledMissingCredentialsPreservesExistingValidationWithoutNetwork() {
        login();when(capabilities.isEnabled()).thenReturn(true);
        var result=controller.testConnection(Map.of(),null);
        assertEquals(200,result.getCode());assertEquals(false,result.getData().get("success"));
    }
}
