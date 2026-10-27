package com.knowflow.exam.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客观题规则判分单元测试（纯函数、确定性）。
 * 覆盖：单选/判断精确匹配、多选全对/少选/错选两种策略、填空等价答案、主观题必须待评（score=null）。
 */
class ExamGraderTest {

    @Nested
    @DisplayName("单选 / 判断：精确匹配")
    class ExactTests {

        @Test
        @DisplayName("单选索引命中得满分")
        void singleCorrect() {
            var o = ExamGrader.grade("SINGLE_CHOICE", "1", "1", null, 5);
            assertThat(o.pending()).isFalse();
            assertThat(o.correct()).isTrue();
            assertThat(o.score()).isEqualTo(5);
        }

        @Test
        @DisplayName("单选未命中得 0 分")
        void singleWrong() {
            var o = ExamGrader.grade("SINGLE_CHOICE", "0", "2", null, 5);
            assertThat(o.correct()).isFalse();
            assertThat(o.score()).isZero();
        }

        @Test
        @DisplayName("判断题忽略大小写匹配 true/false")
        void trueFalseCaseInsensitive() {
            var ok = ExamGrader.grade("TRUE_FALSE", "false", "FALSE", null, 2);
            assertThat(ok.correct()).isTrue();
            assertThat(ok.score()).isEqualTo(2);
        }

        @Test
        @DisplayName("未作答记 0 分但属于规则判分而非待评")
        void emptyAnswerScoresZero() {
            var o = ExamGrader.grade("SINGLE_CHOICE", "0", "  ", null, 5);
            assertThat(o.pending()).isFalse();
            assertThat(o.score()).isZero();
        }

        @Test
        @DisplayName("标准答案缺失属于题库脏数据：转待评，不能算用户答错")
        void missingStandardGoesPending() {
            var o = ExamGrader.grade("SINGLE_CHOICE", "", "1", null, 5);
            assertThat(o.pending()).isTrue();
            assertThat(o.score()).isNull();
        }
    }

    @Nested
    @DisplayName("多选题评分策略")
    class MultipleTests {

        @Test
        @DisplayName("集合完全一致得满分（顺序无关）")
        void exactSetFullScore() {
            var o = ExamGrader.grade("MULTIPLE_CHOICE", "0,2,1", "2,0,1",
                    ExamGrader.POLICY_PARTIAL_CREDIT, 6);
            assertThat(o.correct()).isTrue();
            assertThat(o.score()).isEqualTo(6);
        }

        @Test
        @DisplayName("错选任意项：PARTIAL_CREDIT 下仍为 0 分")
        void wrongPickZeroEvenUnderPartial() {
            var o = ExamGrader.grade("MULTIPLE_CHOICE", "0,1", "0,2",
                    ExamGrader.POLICY_PARTIAL_CREDIT, 6);
            assertThat(o.correct()).isFalse();
            assertThat(o.score()).isZero();
        }

        @Test
        @DisplayName("少选：PARTIAL_CREDIT 按比例给分")
        void partialCreditUnderPick() {
            // 标准 3 项，选对 2 项 → round(9 * 2/3)=6
            var o = ExamGrader.grade("MULTIPLE_CHOICE", "0,1,2", "0,1",
                    ExamGrader.POLICY_PARTIAL_CREDIT, 9);
            assertThat(o.correct()).isFalse();
            assertThat(o.score()).isEqualTo(6);
        }

        @Test
        @DisplayName("少选部分分永远不能达到满分")
        void partialCreditNeverFull() {
            // 标准 2 项，选对 1 项 → round(10 * 0.5)=5，且被 maxScore-1 封顶
            var o = ExamGrader.grade("MULTIPLE_CHOICE", "0,1", "0",
                    ExamGrader.POLICY_PARTIAL_CREDIT, 10);
            assertThat(o.score()).isLessThan(10);
        }

        @Test
        @DisplayName("ALL_OR_NOTHING：少选直接 0 分")
        void allOrNothingUnderPickZero() {
            var o = ExamGrader.grade("MULTIPLE_CHOICE", "0,1,2", "0,1",
                    ExamGrader.POLICY_ALL_OR_NOTHING, 9);
            assertThat(o.score()).isZero();
        }
    }

    @Nested
    @DisplayName("填空题：归一化与等价答案")
    class FillTests {

        @Test
        @DisplayName("trim + 忽略大小写")
        void normalizedMatch() {
            var o = ExamGrader.grade("FILL_BLANK", "G1", " g1 ", null, 3);
            assertThat(o.correct()).isTrue();
            assertThat(o.score()).isEqualTo(3);
        }

        @Test
        @DisplayName("竖线分隔的多个等价答案命中其一即可")
        void alternatives() {
            var o = ExamGrader.grade("FILL_BLANK", "ConcurrentHashMap|CHM|并发map", "chm", null, 3);
            assertThat(o.correct()).isTrue();
        }

        @Test
        @DisplayName("错误答案 0 分")
        void wrongFill() {
            var o = ExamGrader.grade("FILL_BLANK", "0.75", "0.5", null, 3);
            assertThat(o.score()).isZero();
        }
    }

    @Test
    @DisplayName("主观题规则无法评分：必须 pending 且 score=null（不能默认 0 分）")
    void subjectiveAlwaysPending() {
        var empty = ExamGrader.grade("SHORT_ANSWER", null, "", null, 20);
        var answered = ExamGrader.grade("SHORT_ANSWER", "参考答案", "我的作答", null, 20);
        assertThat(empty.pending()).isTrue();
        assertThat(empty.score()).isNull();
        assertThat(answered.pending()).isTrue();
        assertThat(answered.score()).isNull();
        assertThat(ExamGrader.requiresAi("SHORT_ANSWER")).isTrue();
        assertThat(ExamGrader.isObjective("FILL_BLANK")).isTrue();
    }
}
