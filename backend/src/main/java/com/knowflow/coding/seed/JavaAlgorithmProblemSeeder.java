package com.knowflow.coding.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.coding.mapper.CodeProblemMapper;
import com.knowflow.coding.mapper.CodeTestCaseMapper;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.QuizQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 3G 默认 Java 算法题库（首批 8 道，经过 javac/java 真实校验，见 LocalJavaJudgeSeedVerificationTest）。
 *
 * <p>安全/流程约束：
 * <ul>
 *   <li>开关 {@code knowflow.coding.seed.algorithm.enabled=true} 才执行，默认关闭（不污染测试与正式库）；</li>
 *   <li>题目插入后 status=0 / review_status=PENDING_REVIEW，<b>不会</b>自动出现在用户题库或组卷池，
 *       必须管理员在题库中心审核通过才发布（未经授权不得直接导入正式公共题库）；</li>
 *   <li>按 canonicalKey 幂等，已存在则跳过，不覆盖人工修改；</li>
 *   <li>历史试卷快照不受影响（种子只新增，不改动任何已有题目）。</li>
 * </ul>
 */
@Slf4j
@Component
@Order(31)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowflow.coding.seed.algorithm.enabled", havingValue = "true")
public class JavaAlgorithmProblemSeeder implements ApplicationRunner {

    private final QuizQuestionMapper questionMapper;
    private final CodeProblemMapper problemMapper;
    private final CodeTestCaseMapper caseMapper;

    /** 单道种子题的完整定义。cases：[visibility, inputJson, expectedJson]。 */
    record Seed(String key, String title, String topic, int difficulty, String tags,
                String signature, String starter, String content, String constraints,
                String examplesDisplay, String reference, String explanation,
                String complexity, String followUps, String commonMistakes,
                List<String[]> cases) {
    }

    /** 供真实 javac/java 校验测试复用：由种子定义构造 code_problem（无 id）。 */
    static CodeProblem buildProblem(Seed s) {
        CodeProblem p = new CodeProblem();
        p.setSupportedLanguages("java");
        p.setFunctionSignature(s.signature());
        p.setStarterCode(s.starter());
        p.setConstraints(s.constraints());
        p.setExamples(s.examplesDisplay());
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        p.setJudgeMode(CodeProblem.MODE_FUNCTION);
        p.setChecker(CodeProblem.CHECKER_EXACT);
        p.setToleranceValue(0d);
        p.setTestCaseVersion("v1");
        p.setReferenceSolution(s.reference());
        p.setSolutionExplanation(s.explanation());
        p.setComplexityAnalysis(s.complexity());
        p.setFollowUps(s.followUps());
        p.setCommonMistakes(s.commonMistakes());
        return p;
    }

    /** 供真实 javac/java 校验测试复用：由种子定义构造全部用例。 */
    static List<CodeTestCase> buildCases(Seed s) {
        List<CodeTestCase> out = new java.util.ArrayList<>();
        int order = 0;
        for (String[] c : s.cases()) {
            CodeTestCase tc = new CodeTestCase();
            tc.setVisibility(c[0]);
            tc.setInput(c[1]);
            tc.setExpectedOutput(c[2]);
            tc.setWeight(1);
            tc.setOrderNo(order++);
            tc.setVersion("v1");
            out.add(tc);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        int inserted = 0;
        for (Seed s : SEEDS) {
            QuizQuestion existing = questionMapper.selectOne(new LambdaQueryWrapper<QuizQuestion>()
                    .select(QuizQuestion::getId)
                    .eq(QuizQuestion::getCanonicalKey, s.key()).last("LIMIT 1"));
            if (existing != null) {
                continue;
            }
            QuizQuestion q = new QuizQuestion();
            q.setTitle(s.title());
            q.setContent(s.content());
            q.setQuestionType(QuizQuestion.TYPE_CODE);
            q.setDifficulty(s.difficulty());
            q.setTopicCode(s.topic());
            q.setTags(s.tags());
            q.setSource(QuizQuestion.SOURCE_MANUAL);
            q.setStatus(0); // 草稿：等待管理员审核
            q.setReviewStatus(QuizQuestion.REVIEW_PENDING);
            q.setVersion(1);
            q.setCanonicalKey(s.key());
            questionMapper.insert(q);

            CodeProblem p = buildProblem(s);
            p.setQuestionId(q.getId());
            problemMapper.insert(p);

            for (CodeTestCase tc : buildCases(s)) {
                tc.setProblemId(p.getId());
                caseMapper.insert(tc);
            }
            inserted++;
        }
        if (inserted > 0) {
            log.info("[JavaAlgorithmProblemSeeder] 插入待审核算法题 {} 道（canonicalKey 幂等跳过已存在题）", inserted);
        }
    }

    static final List<Seed> SEEDS = List.of(
            new Seed(
                    "ALGO_TWO_SUM",
                    "两数之和",
                    "ALGO_HASH",
                    1,
                    "算法,哈希表,数组,入门",
                    "public int[] twoSum(int[] nums, int target)",
                    """
                    class Solution {
                        public int[] twoSum(int[] nums, int target) {
                            // 在这里实现你的解法
                            return new int[0];
                        }
                    }
                    """,
                    """
                    ## 两数之和

                    给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在数组中找出**和为目标值** `target` 的两个整数，并返回它们的数组下标。

                    你可以假设每种输入只会对应一个答案，并且你不能使用两次相同的元素。

                    **返回约定**：返回长度为 2 的下标数组，下标按**升序**排列（即 `[小下标, 大下标]`）。

                    示例：`nums = [2,7,11,15], target = 9`，因为 `nums[0] + nums[1] == 9`，返回 `[0,1]`。
                    """,
                    "- 2 <= nums.length <= 10^4\n- -10^9 <= nums[i] <= 10^9\n- -10^9 <= target <= 10^9\n- 只会存在一个有效答案",
                    """
                    [{"input":"nums = [2,7,11,15], target = 9","output":"[0,1]","explanation":"2 + 7 = 9"},
                     {"input":"nums = [3,2,4], target = 6","output":"[1,2]","explanation":"2 + 4 = 6"}]
                    """,
                    """
                    import java.util.HashMap;
                    import java.util.Map;

                    class Solution {
                        public int[] twoSum(int[] nums, int target) {
                            Map<Integer, Integer> seen = new HashMap<>();
                            for (int i = 0; i < nums.length; i++) {
                                Integer j = seen.get(target - nums[i]);
                                if (j != null) {
                                    return new int[]{j, i};
                                }
                                seen.put(nums[i], i);
                            }
                            return new int[0];
                        }
                    }
                    """,
                    "一遍扫描配合哈希表：对每个 nums[i]，查表中是否已存在 target-nums[i]；存在即命中，否则把当前值与下标存入表中。由于先查后存，不会使用同一元素两次，且命中时 j<i 天然满足升序。",
                    "时间复杂度 O(n)，每个元素至多查表/填表各一次；空间复杂度 O(n)，哈希表最多存 n-1 个元素。",
                    """
                    ["哈希表扩容因子与初始容量如何选择？","如果存在多个答案，题目契约应如何修改？","输出有序数组的两数之和能否用双指针做到 O(1) 空间？"]
                    """,
                    """
                    ["用嵌套循环暴力枚举导致 O(n^2) 超时","返回下标顺序写反","先 put 再查找导致同一元素被使用两次"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[2,7,11,15],9]", "[0,1]"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[3,2,4],6]", "[1,2]"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[3,3],6]", "[0,1]"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[1,5,8,12],20]", "[2,3]"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[-1,-2,-3,-4,-5],-8]", "[2,4]"}
                    )
            ),
            new Seed(
                    "ALGO_TWO_SUM_SORTED",
                    "两数之和 II - 输入有序数组",
                    "ALGO_TWO_POINTERS",
                    1,
                    "算法,双指针,数组,二分",
                    "public int[] twoSumSorted(int[] numbers, int target)",
                    """
                    class Solution {
                        public int[] twoSumSorted(int[] numbers, int target) {
                            // 在这里实现你的解法
                            return new int[0];
                        }
                    }
                    """,
                    """
                    ## 两数之和 II - 输入有序数组

                    给你一个下标从 **1** 开始的整数数组 `numbers`，该数组已是按**非递减顺序**排列的有序数组。
                    请你从数组中找出满足相加之和等于目标数 `target` 的两个数。

                    **返回约定**：返回长度为 2 的下标数组 `[index1, index2]`，下标**从 1 开始计数**，且 `index1 < index2`。
                    题目保证恰好有一个解。

                    示例：`numbers = [2,7,11,15], target = 9`，返回 `[1,2]`。
                    """,
                    "- 2 <= numbers.length <= 3 * 10^4\n- -1000 <= numbers[i] <= 1000\n- numbers 按非递减顺序排列\n- 恰好存在一个解",
                    """
                    [{"input":"numbers = [2,7,11,15], target = 9","output":"[1,2]"},
                     {"input":"numbers = [2,3,4], target = 6","output":"[1,3]"}]
                    """,
                    """
                    class Solution {
                        public int[] twoSumSorted(int[] numbers, int target) {
                            int left = 0;
                            int right = numbers.length - 1;
                            while (left < right) {
                                int sum = numbers[left] + numbers[right];
                                if (sum == target) {
                                    return new int[]{left + 1, right + 1};
                                }
                                if (sum < target) {
                                    left++;
                                } else {
                                    right--;
                                }
                            }
                            return new int[0];
                        }
                    }
                    """,
                    "首尾双指针：和偏小则左指针右移增大和，和偏大则右指针左移减小和。有序性保证每轮排除都不会漏掉唯一解。",
                    "时间复杂度 O(n)，双指针合计至多移动 n 次；空间复杂度 O(1)，只使用常数额外空间。",
                    """
                    ["为什么双指针不会漏掉正确答案？给出不变量证明。","与哈希表解法相比各自适用场景？","数组中存在重复元素时结论是否仍然成立？"]
                    """,
                    """
                    ["忘记下标从 1 开始，直接返回 0 基下标","sum==target 时仍移动指针导致越界","用二分查找做到 O(n log n) 但比双指针慢"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[2,7,11,15],9]", "[1,2]"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[2,3,4],6]", "[1,3]"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[-1,0],-1]", "[1,2]"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[5,25,75],100]", "[2,3]"}
                    )
            ),
            new Seed(
                    "ALGO_VALID_PARENTHESES",
                    "有效的括号",
                    "ALGO_STACK_QUEUE",
                    1,
                    "算法,栈,字符串",
                    "public boolean isValid(String s)",
                    """
                    class Solution {
                        public boolean isValid(String s) {
                            // 在这里实现你的解法
                            return false;
                        }
                    }
                    """,
                    """
                    ## 有效的括号

                    给定一个只包括 `'('`、`')'`、`'{'`、`'}'`、`'['`、`']'` 的字符串 `s`，判断字符串是否有效。

                    有效字符串需满足：
                    1. 左括号必须用相同类型的右括号闭合；
                    2. 左括号必须以正确的顺序闭合；
                    3. 每个右括号都有一个对应的相同类型左括号。

                    返回布尔值。空字符串视为有效。
                    """,
                    "- 1 <= s.length <= 10^4\n- s 仅由括号 '()[]{}' 组成",
                    """
                    [{"input":"s = \\"()\\"","output":"true"},
                     {"input":"s = \\"()[]{}\\"","output":"true"},
                     {"input":"s = \\"(]\\"","output":"false"}]
                    """,
                    """
                    import java.util.Deque;
                    import java.util.ArrayDeque;

                    class Solution {
                        public boolean isValid(String s) {
                            Deque<Character> stack = new ArrayDeque<>();
                            for (int i = 0; i < s.length(); i++) {
                                char c = s.charAt(i);
                                if (c == '(') {
                                    stack.push(')');
                                } else if (c == '[') {
                                    stack.push(']');
                                } else if (c == '{') {
                                    stack.push('}');
                                } else if (stack.isEmpty() || stack.pop() != c) {
                                    return false;
                                }
                            }
                            return stack.isEmpty();
                        }
                    }
                    """,
                    "栈：遇左括号压入其期望的右括号；遇右括号时栈顶必须恰好与之匹配。扫描结束栈必须为空，否则有未闭合左括号。",
                    "时间复杂度 O(n)，每个字符入/出栈至多一次；空间复杂度 O(n)，最坏情况（全左括号）栈深 n。",
                    """
                    ["如何扩展支持任意配对符号的配置化？","只用计数器能否解决三类括号混合？为什么？","如何实现括号字符串的最小修复次数？"]
                    """,
                    """
                    ["用 Stack 而非 ArrayDeque（遗留同步类）","忘记检查栈是否为空就 pop","循环结束忘记判断 stack.isEmpty()"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"()\"]", "true"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"()[]{}\"]", "true"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"(]\"]", "false"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"{[]}\"]", "true"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"([)]\"]", "false"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"]\"]", "false"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"(\"]", "false"}
                    )
            ),
            new Seed(
                    "ALGO_BINARY_SEARCH",
                    "二分查找",
                    "ALGO_BINARY_SEARCH",
                    1,
                    "算法,二分查找,数组",
                    "public int search(int[] nums, int target)",
                    """
                    class Solution {
                        public int search(int[] nums, int target) {
                            // 在这里实现你的解法
                            return -1;
                        }
                    }
                    """,
                    """
                    ## 二分查找

                    给定一个有 `n` 个元素有序的（升序）整型数组 `nums` 和一个目标值 `target`，
                    写一个函数搜索 `nums` 中的 `target`，如果目标值存在返回下标，否则返回 `-1`。

                    数组中的每个元素都是唯一的。
                    """,
                    "- 1 <= nums.length <= 10^4\n- -10^4 < nums[i], target < 10^4\n- nums 中所有元素互不相同\n- nums 按升序排列",
                    """
                    [{"input":"nums = [-1,0,3,5,9,12], target = 9","output":"4"},
                     {"input":"nums = [-1,0,3,5,9,12], target = 2","output":"-1"}]
                    """,
                    """
                    class Solution {
                        public int search(int[] nums, int target) {
                            int left = 0;
                            int right = nums.length - 1;
                            while (left <= right) {
                                int mid = left + (right - left) / 2;
                                if (nums[mid] == target) {
                                    return mid;
                                }
                                if (nums[mid] < target) {
                                    left = mid + 1;
                                } else {
                                    right = mid - 1;
                                }
                            }
                            return -1;
                        }
                    }
                    """,
                    "维护闭区间 [left, right]：mid 命中即返回；mid 值偏小则下界越到 mid+1，偏大则上界越到 mid-1。区间为空仍未命中则目标不存在。",
                    "时间复杂度 O(log n)，每轮搜索区间减半；空间复杂度 O(1)。",
                    """
                    ["left<=right 与 left<right 两种写法的循环不变量差异？","为什么推荐 left+(right-left)/2 而不是 (left+right)/2？","元素有重复时如何找左/右边界？"]
                    """,
                    """
                    ["边界更新写成 left=mid 造成死循环","mid 计算 (left+right)/2 在大数组上整数溢出","target 不存在时忘记返回 -1"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[-1,0,3,5,9,12],9]", "4"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[-1,0,3,5,9,12],2]", "-1"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[1],1]", "0"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[1],0]", "-1"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[1,3,5,7],7]", "3"}
                    )
            ),
            new Seed(
                    "ALGO_MAX_SUBARRAY",
                    "最大子数组和",
                    "ALGO_DP",
                    2,
                    "算法,动态规划,数组,Kadane",
                    "public int maxSubArray(int[] nums)",
                    """
                    class Solution {
                        public int maxSubArray(int[] nums) {
                            // 在这里实现你的解法
                            return 0;
                        }
                    }
                    """,
                    """
                    ## 最大子数组和

                    给你一个整数数组 `nums`，请你找出一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。

                    示例：`nums = [-2,1,-3,4,-1,2,1,-5,4]`，连续子数组 `[4,-1,2,1]` 的和最大，为 `6`。
                    """,
                    "- 1 <= nums.length <= 10^5\n- -10^4 <= nums[i] <= 10^4",
                    """
                    [{"input":"nums = [-2,1,-3,4,-1,2,1,-5,4]","output":"6","explanation":"[4,-1,2,1] 和为 6"},
                     {"input":"nums = [1]","output":"1"}]
                    """,
                    """
                    class Solution {
                        public int maxSubArray(int[] nums) {
                            int best = nums[0];
                            int endingHere = nums[0];
                            for (int i = 1; i < nums.length; i++) {
                                endingHere = Math.max(nums[i], endingHere + nums[i]);
                                best = Math.max(best, endingHere);
                            }
                            return best;
                        }
                    }
                    """,
                    "Kadane 算法：定义 endingHere 为以当前元素结尾的最大子数组和，它要么接上前面的段，要么从当前元素重新起段；全程维护最大值 best。",
                    "时间复杂度 O(n)，一次扫描；空间复杂度 O(1)，只维护两个滚动变量。",
                    """
                    ["如何返回最大子数组本身（下标范围）？","分治法 O(n log n) 的思路与线段树扩展？","环形数组的最大子数组和如何求解？"]
                    """,
                    """
                    ["把 best 初始化为 0，全负数组会返回错误答案 0","状态转移漏掉从当前元素重新起段的选择","用前缀和但忘记前缀最小值可能在当前位置之后"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[-2,1,-3,4,-1,2,1,-5,4]]", "6"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[[1]]", "1"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[5,4,-1,7,8]]", "23"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[-1]]", "-1"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[[-2,-1,-3]]", "-1"}
                    )
            ),
            new Seed(
                    "ALGO_LONGEST_SUBSTRING",
                    "无重复字符的最长子串",
                    "ALGO_SLIDING_WINDOW",
                    2,
                    "算法,滑动窗口,哈希表,字符串",
                    "public int lengthOfLongestSubstring(String s)",
                    """
                    class Solution {
                        public int lengthOfLongestSubstring(String s) {
                            // 在这里实现你的解法
                            return 0;
                        }
                    }
                    """,
                    """
                    ## 无重复字符的最长子串

                    给定一个字符串 `s`，请你找出其中不含有重复字符的**最长子串**的长度。

                    示例：`s = "abcabcbb"`，无重复字符的最长子串是 `"abc"`，长度为 `3`。
                    """,
                    "- 0 <= s.length <= 5 * 10^4\n- s 由英文字母、数字、符号和空格组成",
                    """
                    [{"input":"s = \\"abcabcbb\\"","output":"3"},
                     {"input":"s = \\"bbbbb\\"","output":"1"},
                     {"input":"s = \\"pwwkew\\"","output":"3","explanation":"最长子串是 \\"wke\\""}]
                    """,
                    """
                    import java.util.HashMap;
                    import java.util.Map;

                    class Solution {
                        public int lengthOfLongestSubstring(String s) {
                            Map<Character, Integer> lastPos = new HashMap<>();
                            int left = 0;
                            int best = 0;
                            for (int right = 0; right < s.length(); right++) {
                                char c = s.charAt(right);
                                Integer seen = lastPos.get(c);
                                if (seen != null && seen >= left) {
                                    left = seen + 1;
                                }
                                lastPos.put(c, right);
                                best = Math.max(best, right - left + 1);
                            }
                            return best;
                        }
                    }
                    """,
                    "滑动窗口 + 字符最近位置表：右指针扩张；当当前字符在窗口内出现过时，左边界直接跳到重复位置的下一位。注意跳跃条件必须是上次位置 >= left，否则是窗外的历史记录。",
                    "时间复杂度 O(n)，每个字符至多被左右指针各访问一次；空间复杂度 O(min(n, 字符集大小))。",
                    """
                    ["为什么 seen >= left 的判断必不可少？","用 boolean[128] 与 HashMap 各有什么取舍？","要求返回该子串内容时如何改造？"]
                    """,
                    """
                    ["left 只加 1 而不是跳到重复位+1，退化为 O(n^2)","忘记 seen>=left 条件，被窗外旧记录干扰","窗口长度计算差一（忘记 +1）"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"abcabcbb\"]", "3"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"bbbbb\"]", "1"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"pwwkew\"]", "3"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"\"]", "0"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"abba\"]", "2"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"dvdf\"]", "3"}
                    )
            ),
            new Seed(
                    "ALGO_CLIMB_STAIRS",
                    "爬楼梯",
                    "ALGO_DP",
                    1,
                    "算法,动态规划,斐波那契",
                    "public int climbStairs(int n)",
                    """
                    class Solution {
                        public int climbStairs(int n) {
                            // 在这里实现你的解法
                            return 0;
                        }
                    }
                    """,
                    """
                    ## 爬楼梯

                    假设你正在爬楼梯。需要 `n` 阶你才能到达楼顶。
                    每次你可以爬 `1` 或 `2` 个台阶。你有多少种不同的方法可以爬到楼顶呢？

                    示例：`n = 3` 时，可以按 `1+1+1`、`1+2`、`2+1` 三种方式到达，答案为 `3`。
                    """,
                    "- 1 <= n <= 45（答案在 int 范围内）",
                    """
                    [{"input":"n = 2","output":"2","explanation":"1+1 或 2"},
                     {"input":"n = 3","output":"3"}]
                    """,
                    """
                    class Solution {
                        public int climbStairs(int n) {
                            int prev2 = 1;
                            int prev1 = 1;
                            for (int i = 2; i <= n; i++) {
                                int cur = prev1 + prev2;
                                prev2 = prev1;
                                prev1 = cur;
                            }
                            return prev1;
                        }
                    }
                    """,
                    "动态规划：到达第 i 阶的方法数 = 到达 i-1 阶（跨 1 步）+ 到达 i-2 阶（跨 2 步），即 f(i)=f(i-1)+f(i-2)，初值 f(0)=f(1)=1。用两个滚动变量把空间压到 O(1)。",
                    "时间复杂度 O(n)；空间复杂度 O(1)。n 更大时可用矩阵快速幂/通项公式优化到 O(log n)。",
                    """
                    ["n 上限放大到 10^9 怎么做？（矩阵快速幂）","n=45 之后结果溢出 int 应改用什么类型？","每次可跨 1/2/3 阶时递推如何变化？"]
                    """,
                    """
                    ["递归不带记忆化导致指数级重复计算","初值 f(0)/f(1) 混淆造成整体错位","用 int[] dp 却忘记空间可以滚动优化"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[2]", "2"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[3]", "3"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[1]", "1"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[10]", "89"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[20]", "10946"}
                    )
            ),
            new Seed(
                    "ALGO_ROMAN_TO_INT",
                    "罗马数字转整数",
                    "ALGO_HASH",
                    1,
                    "算法,哈希表,字符串,模拟",
                    "public int romanToInt(String s)",
                    """
                    class Solution {
                        public int romanToInt(String s) {
                            // 在这里实现你的解法
                            return 0;
                        }
                    }
                    """,
                    """
                    ## 罗马数字转整数

                    罗马数字包含以下七种字符：`I=1`、`V=5`、`X=10`、`L=50`、`C=100`、`D=500`、`M=1000`。

                    通常情况下，罗马数字中小的数字在大的数字右边。但也存在特例，例如 `IV=4`、`IX=9`、`XL=40`、
                    `XC=90`、`CD=400`、`CM=900`。

                    给定一个合法罗马数字，将其转换成整数。
                    """,
                    "- 1 <= s.length <= 15\n- s 仅含字符 ('I','V','X','L','C','D','M')\n- 数据保证是合法罗马数字，范围 [1, 3999]",
                    """
                    [{"input":"s = \\"III\\"","output":"3"},
                     {"input":"s = \\"IV\\"","output":"4"},
                     {"input":"s = \\"IX\\"","output":"9"}]
                    """,
                    """
                    class Solution {
                        public int romanToInt(String s) {
                            int[] values = new int[26];
                            values['I' - 'A'] = 1;
                            values['V' - 'A'] = 5;
                            values['X' - 'A'] = 10;
                            values['L' - 'A'] = 50;
                            values['C' - 'A'] = 100;
                            values['D' - 'A'] = 500;
                            values['M' - 'A'] = 1000;
                            int total = 0;
                            for (int i = 0; i < s.length(); i++) {
                                int v = values[s.charAt(i) - 'A'];
                                if (i + 1 < s.length() && v < values[s.charAt(i + 1) - 'A']) {
                                    total -= v;
                                } else {
                                    total += v;
                                }
                            }
                            return total;
                        }
                    }
                    """,
                    "从左到右扫描：若当前字符值小于右侧字符值（如 IV 中的 I），它是减法记号，减去；否则累加。合法输入保证减法配对只会出现在六种特例中。",
                    "时间复杂度 O(n)；空间复杂度 O(1)（26 长度的定长数组）。",
                    """
                    ["反向扫描的写法为什么也成立？","整数转罗马数字如何贪心实现？","为什么题目保证输入合法后可以省略大量校验？"]
                    """,
                    """
                    ["把 IV 处理成 1+5=6 而不是 4","查表用 Map<Character,Integer> 装箱较慢（本题不要求但应知）","遍历到最后一位时访问越界"]
                    """,
                    List.of(
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"III\"]", "3"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"IV\"]", "4"},
                            new String[]{CodeTestCase.VIS_PUBLIC, "[\"IX\"]", "9"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"LVIII\"]", "58"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"MCMXCIV\"]", "1994"},
                            new String[]{CodeTestCase.VIS_HIDDEN, "[\"XLII\"]", "42"}
                    )
            )
    );
}
