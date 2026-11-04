package com.knowflow.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

/** 校验内置工具声明的对象字段，拒绝未声明的参数。 */
final class ToolArguments {
  private ToolArguments() {}

  static boolean valid(JsonNode schema, JsonNode arguments) {
    if (arguments == null
        || !arguments.isObject()
        || !"object".equals(schema.path("type").asText())) {
      return false;
    }
    for (JsonNode required : schema.path("required")) {
      if (!arguments.hasNonNull(required.asText())) {
        return false;
      }
    }
    var fields = arguments.fields();
    while (fields.hasNext()) {
      var field = fields.next();
      JsonNode rule = schema.path("properties").path(field.getKey());
      if (rule.isMissingNode() || !matches(rule, field.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static boolean matches(JsonNode rule, JsonNode value) {
    boolean validType =
        switch (rule.path("type").asText()) {
          case "string" -> value.isTextual();
          case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
          case "number" -> value.isNumber();
          case "boolean" -> value.isBoolean();
          case "array" -> value.isArray();
          case "object" -> value.isObject();
          default -> false;
        };
    if (!validType) {
      return false;
    }
    if (rule.has("enum") && !contains(rule.get("enum"), value)) {
      return false;
    }
    if (value.isTextual()) {
      int length = value.textValue().codePointCount(0, value.textValue().length());
      return length >= rule.path("minLength").asInt(0)
          && length <= rule.path("maxLength").asInt(Integer.MAX_VALUE);
    }
    if (value.isNumber()) {
      return (!rule.has("minimum")
              || value.decimalValue().compareTo(rule.get("minimum").decimalValue()) >= 0)
          && (!rule.has("maximum")
              || value.decimalValue().compareTo(rule.get("maximum").decimalValue()) <= 0);
    }
    if (value.isArray() && rule.has("items")) {
      for (JsonNode item : value) {
        if (!matches(rule.get("items"), item)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean contains(JsonNode values, JsonNode value) {
    for (JsonNode allowed : values) {
      if (allowed.equals(value)) {
        return true;
      }
    }
    return false;
  }
}
