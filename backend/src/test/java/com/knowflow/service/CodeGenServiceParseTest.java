package com.knowflow.service;

import com.knowflow.vo.GeneratedFileVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeGenService 输出解析逻辑单元测试。
 * <p>
 * 解析层是本功能最容易出错的环节（模型输出格式并不稳定），这里覆盖三级降级与安全校验。
 * 通过反射调用私有解析方法，避免为了测试而放宽方法可见性。
 */
class CodeGenServiceParseTest {

    private final CodeGenService service = new CodeGenService(null);

    @SuppressWarnings("unchecked")
    private List<GeneratedFileVO> parse(String raw) throws Exception {
        Method m = CodeGenService.class.getDeclaredMethod("parseFiles", String.class);
        m.setAccessible(true);
        return (List<GeneratedFileVO>) m.invoke(service, raw);
    }

    private String explain(String raw) throws Exception {
        Method m = CodeGenService.class.getDeclaredMethod("extractExplanation", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, raw);
    }

    @Test
    @DisplayName("一级解析：FILE: 标注 + 代码块")
    void parseNamedBlocks() throws Exception {
        String raw = """
                好的，下面是一个简单的演示页面。

                FILE: index.html
                ```html
                <!DOCTYPE html>
                <html><body><h1>Hello</h1></body></html>
                ```

                FILE: style.css
                ```css
                body { margin: 0; }
                ```

                双击 index.html 即可打开。
                """;
        List<GeneratedFileVO> files = parse(raw);
        assertEquals(2, files.size());
        assertEquals("index.html", files.get(0).getFileName());
        assertEquals("html", files.get(0).getLanguage());
        assertTrue(files.get(0).getContent().contains("<h1>Hello</h1>"));
        assertEquals("style.css", files.get(1).getFileName());
        assertEquals("css", files.get(1).getLanguage());
    }

    @Test
    @DisplayName("一级解析：兼容 **FILE: x** 加粗与中文冒号写法")
    void parseNamedBlocksWithDecoration() throws Exception {
        String raw = """
                **FILE：app.js**
                ```javascript
                console.log('hi')
                ```
                """;
        List<GeneratedFileVO> files = parse(raw);
        assertEquals(1, files.size());
        assertEquals("app.js", files.get(0).getFileName());
        assertEquals("javascript", files.get(0).getLanguage());
    }

    @Test
    @DisplayName("二级解析：无文件名标注时按语言推断，同语言追加序号")
    void parseAnonymousBlocks() throws Exception {
        String raw = """
                ```html
                <!DOCTYPE html><html></html>
                ```
                ```html
                <div>second</div>
                ```
                """;
        List<GeneratedFileVO> files = parse(raw);
        assertEquals(2, files.size());
        assertEquals("index.html", files.get(0).getFileName());
        // 第二个同语言块必须换名，否则会覆盖第一个文件
        assertEquals("index2.html", files.get(1).getFileName());
    }

    @Test
    @DisplayName("三级解析：模型直接吐出裸 HTML 也能兜底")
    void parseBareHtml() throws Exception {
        String raw = "<!DOCTYPE html>\n<html><body>demo</body></html>";
        List<GeneratedFileVO> files = parse(raw);
        assertEquals(1, files.size());
        assertEquals("index.html", files.get(0).getFileName());
        assertTrue(files.get(0).getContent().endsWith("</html>"));
    }

    @Test
    @DisplayName("安全：拒绝路径穿越与绝对路径文件名")
    void rejectUnsafeFileName() throws Exception {
        String raw = """
                FILE: ../../etc/passwd
                ```text
                malicious
                ```
                """;
        List<GeneratedFileVO> files = parse(raw);
        // 危险文件名被丢弃后，不应产生任何带该名字的文件
        assertTrue(files.stream().noneMatch(f -> f.getFileName().contains("..")));
    }

    @Test
    @DisplayName("无代码块时返回空列表，交由上层提示用户")
    void emptyWhenNoCode() throws Exception {
        assertTrue(parse("我不太理解你的需求，请补充说明。").isEmpty());
    }

    @Test
    @DisplayName("说明文字提取：剔除代码块与 FILE 标注行")
    void extractExplanationText() throws Exception {
        String raw = """
                这是一个演示页面。

                FILE: index.html
                ```html
                <html></html>
                ```
                """;
        String text = explain(raw);
        assertTrue(text.contains("这是一个演示页面"));
        assertFalse(text.contains("<html>"));
        assertFalse(text.contains("FILE:"));
    }
}
