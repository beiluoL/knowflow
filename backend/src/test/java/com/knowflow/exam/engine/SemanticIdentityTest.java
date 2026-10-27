package com.knowflow.exam.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语义身份指纹与知识点解析测试。
 */
class SemanticIdentityTest {

    @Test
    void 空白与中英文标点噪声不影响指纹() {
        String a = SemanticIdentity.fingerprint("SINGLE_CHOICE", "HashMap 线程安全?", "并发 put 有问题吗？");
        String b = SemanticIdentity.fingerprint("SINGLE_CHOICE", "hashmap线程安全？", "并发put有问题吗");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void 不同题型产生不同身份() {
        String a = SemanticIdentity.fingerprint("SINGLE_CHOICE", "HashMap 原理", "题干");
        String b = SemanticIdentity.fingerprint("MULTIPLE_CHOICE", "HashMap 原理", "题干");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void 指纹稳定且非空() {
        String fp = SemanticIdentity.fingerprint("SHORT_ANSWER", "JVM 排查", "描述一次 Full GC 排查");
        assertThat(fp).isNotBlank();
        assertThat(fp).isEqualTo(SemanticIdentity.fingerprint("SHORT_ANSWER", "JVM 排查", "描述一次 Full GC 排查"));
    }

    @Test
    void 知识点取首标签并大写() {
        assertThat(SemanticIdentity.topicOf("hashmap,Java集合,SEED")).isEqualTo("HASHMAP");
        assertThat(SemanticIdentity.topicOf("RAG")).isEqualTo("RAG");
        assertThat(SemanticIdentity.topicOf(null)).isEmpty();
        assertThat(SemanticIdentity.topicOf("  ")).isEmpty();
    }
}
