package com.knowflow.agent.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 验证工具参数拒绝非法根结构、字段及约束值。
 */
class ToolArgumentsTest {
  @Test
  void rejectsInvalidRootsFieldsAndConstraints() throws Exception {
    var json = new ObjectMapper();
    var schema =
        json.readTree(
            """
            {"type":"object","required":["action"],"properties":{
              "action":{"type":"string","enum":["read"]},
              "limit":{"type":"integer","minimum":1,"maximum":10},
              "path":{"type":"string","minLength":1,"maxLength":20}
            }}
            """);
    for (String arguments :
        new String[] {
          "[]",
          "{}",
          "{\"action\":null}",
          "{\"action\":\"write\"}",
          "{\"action\":\"read\",\"extra\":1}",
          "{\"action\":\"read\",\"limit\":\"5\"}",
          "{\"action\":\"read\",\"limit\":11}",
          "{\"action\":\"read\",\"path\":\"\"}"
        }) {
      assertFalse(ToolArguments.valid(schema, json.readTree(arguments)), arguments);
    }
    assertTrue(ToolArguments.valid(schema, json.readTree("{\"action\":\"read\",\"limit\":5}")));
  }
}
