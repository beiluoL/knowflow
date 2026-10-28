import { it, expect } from "vitest";
import { formatSource } from "../engine";
// Java格式化只解析文本，不编译或执行用户代码。
it("formats Java without changing string content and rejects invalid syntax", async () => {
  const r = await formatSource(
    'class Main{String s="a  b";int f(){return 1;}}',
    "java",
    0,
    0,
  );
  expect(r.formatted).toContain("class Main {");
  expect(r.formatted).toContain('"a  b"');
  expect(r.formatted).toContain("\n");
  await expect(formatSource("class {", "java", 0, 0)).rejects.toThrow();
});
