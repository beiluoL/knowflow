package com.knowflow.interview.career;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.*;

class CareerPipelineEventTest {
    @Test
    void recordsOnlyRealTransitionsAndUsesOwnerScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CareerRecordService records = mock(CareerRecordService.class);
        when(records.detail(7L, 11L, null)).thenReturn(Map.of("kind", "JOB"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new CareerJobPipelineService(jdbc, records);
        service.update(7L, 11L, "TO_APPLY", "准备投递");
        verify(jdbc).update(contains("INSERT INTO interview_career_job_event"),
                eq(11L), eq(7L), eq("STATUS_CHANGED"), eq("SAVED"), eq("TO_APPLY"), eq("准备投递"));
    }
}
