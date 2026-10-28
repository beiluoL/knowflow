package com.knowflow.interview.verify.arraylist;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArrayListVerifyTest {

    /** 反射读取私有字段 elementData 的长度（容量），用于白盒验证扩容。 */
    @SuppressWarnings("unchecked")
    static int capacity(ArrayList<?> list) throws Exception {
        Field f = ArrayList.class.getDeclaredField("elementData");
        f.setAccessible(true);
        Object[] data = (Object[]) f.get(list);
        return data.length;
    }

    @Test
    void initialCapacityIsTenAfterFirstAdd() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        assertThat(capacity(list)).isEqualTo(0); // 延迟初始化：空数组
        list.add("a");
        assertThat(capacity(list)).isEqualTo(10);
    }

    @Test
    void growsByOneAndHalfAfterElevenAdds() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < 11; i++) list.add("e" + i); // 第 11 个触发扩容 10 -> 15
        assertThat(list).hasSize(11);
        assertThat(capacity(list)).isEqualTo(15);
    }

    @Test
    void ensureCapacityPreallocates() throws Exception {
        ArrayList<String> list = new ArrayList<>(2);
        list.ensureCapacity(100);
        assertThat(capacity(list)).isGreaterThanOrEqualTo(100);
    }

    @Test
    void forEachListRemoveTriggersFailFast() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        list.add("d");
        assertThrows(ConcurrentModificationException.class, () -> {
            for (String s : list) {
                if ("a".equals(s)) list.remove(s); // 移除首元素后迭代继续 → next() 检测到 modCount 变化 → CME
            }
        });
    }

    @Test
    void iteratorRemoveIsSafe() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            if ("b".equals(it.next())) it.remove(); // 同步 expectedModCount，不抛
        }
        assertThat(list).containsExactly("a", "c");
    }

    @Test
    void subListIsView() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        list.add("d");
        List<String> sub = list.subList(1, 3); // [b, c]
        assertThat(sub).containsExactly("b", "c");
        sub.set(0, "B");
        assertThat(list).containsExactly("a", "B", "c", "d"); // 改动反映到原 list
    }
    @Test
    void explicitZeroCapacityDiffersFromDefaultSentinel() throws Exception {
        ArrayList<String> list = new ArrayList<>(0);
        list.add("a");
        assertThat(capacity(list)).isEqualTo(1); // 显式零不是默认构造的特殊空数组标记。
    }

    @Test
    void invalidRemovalCanEndIterationWithoutCme() {
        List<String> list = new ArrayList<>(List.of("a", "b"));
        var iterator = list.iterator();
        assertThat(iterator.next()).isEqualTo("a");
        list.remove("a"); // cursor=1，size=1；hasNext直接false，不保证触发next的检查。
        assertThat(iterator.hasNext()).isFalse();
        assertThat(list).containsExactly("b");
    }
}
