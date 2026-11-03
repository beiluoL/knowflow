package com.knowflow.interview.career.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 薪资解析：只认明确区间，面议不当作 0。 */
class CareerInsightsSalaryTest {

    private final CareerInsightsService service = new CareerInsightsService(null, new ObjectMapper());

    @Test
    void parsesKRange() {
        Integer[] salary = service.parseSalary("薪资 15-25K，另有补贴");
        assertArrayEquals(new Integer[]{15, 25}, salary);
    }

    @Test
    void parsesUpperAndLowerK() {
        assertArrayEquals(new Integer[]{20, 35}, service.parseSalary("20k-35k·14薪"));
    }

    @Test
    void negotiableIsNullNotZero() {
        assertNull(service.parseSalary("薪资面议"));
        assertNull(service.parseSalary("待遇从优"));
        assertNull(service.parseSalary(null));
    }

    @Test
    void salarySummaryKeepsUnparsedCountAndNullWhenEmpty() {
        var empty = service.salarySummary(List.of(), 0, 3);
        assertNull(empty.get("median"));
        assertEquals(3, empty.get("unparsed"));

        var summary = service.salarySummary(List.of(15, 25, 20), 3, 1);
        assertEquals(20, summary.get("median"));
        assertEquals(15, summary.get("min"));
        assertEquals(25, summary.get("max"));
    }
}
