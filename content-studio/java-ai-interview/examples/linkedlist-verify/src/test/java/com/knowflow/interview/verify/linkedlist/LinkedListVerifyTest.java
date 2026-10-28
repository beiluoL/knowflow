package com.knowflow.interview.verify.linkedlist;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedListVerifyTest {

    @Test
    void addFirstAndAddLastProduceCorrectOrder() {
        LinkedList<String> list = new LinkedList<>();
        list.addFirst("b");
        list.addLast("c");
        list.addFirst("a");      // 头部插
        assertThat(list).containsExactly("a", "b", "c");
    }

    @Test
    void getReturnsCorrectValue() {
        LinkedList<String> list = new LinkedList<>();
        list.add("x");
        list.add("y");
        list.add("z");
        assertThat(list.get(0)).isEqualTo("x");
        assertThat(list.get(2)).isEqualTo("z");
    }

    @Test
    void isNotRandomAccess() {
        List<String> list = new LinkedList<>();
        assertThat(list).isNotInstanceOf(RandomAccess.class); // 随机访问是 O(n)
    }

    @Test
    void listIteratorIsBidirectional() {
        LinkedList<String> list = new LinkedList<>();
        list.add("p");
        list.add("q");
        list.add("r");
        ListIterator<String> it = list.listIterator();
        assertThat(it.next()).isEqualTo("p");
        assertThat(it.next()).isEqualTo("q");
        assertThat(it.previous()).isEqualTo("q"); // 回退
        assertThat(it.previous()).isEqualTo("p");
    }

    @Test
    void removeByValueReducesSizeAndContent() {
        LinkedList<String> list = new LinkedList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        assertThat(list.remove("b")).isTrue();
        assertThat(list).containsExactly("a", "c");
        assertThat(list).hasSize(2);
    }

    @Test
    void pushPopStackSemantics() {
        Deque<String> dq = new LinkedList<>();
        dq.push("first");
        dq.push("second");
        assertThat(dq.pop()).isEqualTo("second"); // 后进先出
        assertThat(dq.peek()).isEqualTo("first");
    }

    @Test
    void removeMiddleElementKeepsOthers() {
        LinkedList<String> list = new LinkedList<>();
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.remove(1); // 移除下标 1 -> "2"
        assertThat(list).containsExactly("1", "3", "4");
    }

    @Test
    void dequeOfferPollFromBothEnds() {
        Deque<String> dq = new LinkedList<>();
        dq.offer("tail");
        dq.addFirst("head");
        assertThat(dq.pollFirst()).isEqualTo("head");
        assertThat(dq.pollLast()).isEqualTo("tail");
        assertThat(dq).isEmpty();
    }
}
