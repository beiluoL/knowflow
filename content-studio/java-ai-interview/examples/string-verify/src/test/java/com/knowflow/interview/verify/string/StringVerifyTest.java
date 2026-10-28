package com.knowflow.interview.verify.string;

import org.junit.jupiter.api.Test;

import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

class StringVerifyTest {

    @Test
    void literalPoolSharesReference() {
        String a = "abc";
        String b = "abc";
        assertThat(a == b).isTrue(); // 池共享，同一引用
    }

    @Test
    void newStringDiffersByReferenceButEqualsByContent() {
        String a = "abc";
        String c = new String("abc");
        assertThat(a == c).isFalse();   // 堆上新对象
        assertThat(a.equals(c)).isTrue(); // 内容相等
    }

    @Test
    void internReturnsPoolReference() {
        String c = new String("abc");
        assertThat(c.intern() == "abc").isTrue(); // 回池，与字面量同引用
    }

    @Test
    void toUpperCaseDoesNotMutateOriginal() {
        String s = "hello";
        String u = s.toUpperCase();
        assertThat(s).isEqualTo("hello");  // 原串不变
        assertThat(u).isEqualTo("HELLO");  // 返回新串
    }

    @Test
    void concatReturnsNewString() {
        String s = "hi";
        String r = s.concat("!");
        assertThat(s).isEqualTo("hi");    // 不变
        assertThat(r).isEqualTo("hi!");
    }

    @Test
    void equalsVsIdentity() {
        String a = new String("a");
        assertThat(a.equals("a")).isTrue();
        assertThat(a == "a").isFalse();
    }

    @Test
    void loopConcatWithStringBuilderIsCorrect() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(i);
        assertThat(sb.toString()).isEqualTo("012"); // 循环拼接应用 StringBuilder
    }

    @Test
    void stringBufferAlsoWorks() {
        StringBuffer sb = new StringBuffer();
        sb.append("x").append("y");
        assertThat(sb.toString()).isEqualTo("xy");
    }

    @Test
    void stringJoinerJoinsWithDelimiter() {
        String joined = new StringJoiner(",").add("a").add("b").add("c").toString();
        assertThat(joined).isEqualTo("a,b,c");
    }
}
