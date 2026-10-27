package com.knowflow.coding.util;

import com.knowflow.coding.vo.CodingVos.SubmissionDiffVO.DiffLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性行级代码对比（Phase 3H）。
 *
 * <p>纯函数、无 AI、无随机性：相同两份代码任何时候产生相同结果。
 * 算法为经典 LCS 动态规划 + 回溯，输出统一的 EQUAL/REMOVED/ADDED 行序列（unified 风格，
 * 但不依赖外部 diff 命令，避免宿主机进程调用）。
 *
 * <p>安全/资源：单次对比任一侧超过 {@link #MAX_LINES} 行时截断（truncated=true），
 * 保证 O(n*m) DP 内存有上界；教学场景代码通常远小于该上限。
 */
public final class CodeDiff {

    /** 单侧参与 DP 的最大行数；超出截断，防止超大提交耗尽内存。 */
    public static final int MAX_LINES = 2000;

    public static final String EQUAL = "EQUAL";
    public static final String ADDED = "ADDED";
    public static final String REMOVED = "REMOVED";

    private CodeDiff() {
    }

    /** 对比结果：lines + truncated。 */
    public record Result(List<DiffLine> lines, boolean truncated, int changedLines) {
    }

    /**
     * 计算 oldSource → newSource 的行级差异。
     *
     * @param oldSource 基准代码（from）
     * @param newSource 目标代码（to）
     */
    public static Result diff(String oldSource, String newSource) {
        String[] a = splitLines(oldSource);
        String[] b = splitLines(newSource);

        boolean truncated = false;
        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            a = truncate(a);
            b = truncate(b);
            truncated = true;
        }

        int n = a.length;
        int m = b.length;
        // LCS 长度表
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // 从 (0,0) 正向回溯，输出天然为源码顺序；dp 已从底向上构建。
        List<DiffLine> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        int changed = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                lines.add(new DiffLine(EQUAL, i + 1, j + 1, a[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                // 平局优先删除，保证差异块内先 REMOVED 后 ADDED 的可读顺序
                lines.add(new DiffLine(REMOVED, i + 1, null, a[i]));
                changed++;
                i++;
            } else {
                lines.add(new DiffLine(ADDED, null, j + 1, b[j]));
                changed++;
                j++;
            }
        }
        while (i < n) {
            lines.add(new DiffLine(REMOVED, i + 1, null, a[i]));
            changed++;
            i++;
        }
        while (j < m) {
            lines.add(new DiffLine(ADDED, null, j + 1, b[j]));
            changed++;
            j++;
        }
        return new Result(lines, truncated, changed);
    }

    private static String[] splitLines(String source) {
        if (source == null || source.isEmpty()) {
            return new String[0];
        }
        // 统一 CRLF/CR → LF，再按行切分；保留空行
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        // 末尾单个换行不产生多余空行
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.split("\n", -1);
    }

    private static String[] truncate(String[] lines) {
        if (lines.length <= MAX_LINES) {
            return lines;
        }
        String[] out = new String[MAX_LINES];
        System.arraycopy(lines, 0, out, 0, MAX_LINES);
        return out;
    }
}
