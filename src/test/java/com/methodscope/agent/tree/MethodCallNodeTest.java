package com.methodprobe.agent.tree;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for MethodCallNode.
 */
public class MethodCallNodeTest {

    @Test
    public void testConstructor() {
        long startTime = System.nanoTime();
        MethodCallNode node = new MethodCallNode("com.example.Service.doWork", startTime);

        assertEquals("com.example.Service.doWork", node.getMethodName());
        assertEquals(startTime, node.getStartTimeNanos());
        assertNull(node.getParent());
        assertTrue(node.getChildren().isEmpty());
        assertNull(node.getArgs());
        assertNull(node.getSnapshotId());
    }

    @Test
    public void testConstructorWithArgs() {
        long startTime = System.nanoTime();
        Object[] args = new Object[] { "param1", 42 };
        MethodCallNode node = new MethodCallNode("method", startTime, args);

        assertEquals("method", node.getMethodName());
        assertArrayEquals(args, node.getArgs());
    }

    @Test
    public void testSetEndTime() {
        long start = 1000000L;
        long end = 2000000L;
        MethodCallNode node = new MethodCallNode("method", start);
        node.setEndTime(end);

        assertEquals(end, node.getEndTimeNanos());
    }

    @Test
    public void testGetDurationMs() {
        long start = 0L;
        long end = 1_000_000L; // 1ms in nanoseconds

        MethodCallNode node = new MethodCallNode("method", start);
        node.setEndTime(end);

        assertEquals(1.0, node.getDurationMs(), 0.001);
    }

    @Test
    public void testAddChild() {
        MethodCallNode parent = new MethodCallNode("parent", 0);
        MethodCallNode child1 = new MethodCallNode("child1", 100);
        MethodCallNode child2 = new MethodCallNode("child2", 200);

        parent.addChild(child1);
        parent.addChild(child2);

        List<MethodCallNode> children = parent.getChildren();
        assertEquals(2, children.size());
        assertEquals(child1, children.get(0));
        assertEquals(child2, children.get(1));

        // Verify parent links
        assertEquals(parent, child1.getParent());
        assertEquals(parent, child2.getParent());
    }

    @Test
    public void testIsRoot() {
        MethodCallNode root = new MethodCallNode("root", 0);
        MethodCallNode child = new MethodCallNode("child", 100);
        root.addChild(child);

        assertTrue("Root should return true for isRoot()", root.isRoot());
        assertFalse("Child should return false for isRoot()", child.isRoot());
    }

    @Test
    public void testGetDepth() {
        MethodCallNode root = new MethodCallNode("root", 0);
        MethodCallNode level1 = new MethodCallNode("level1", 100);
        MethodCallNode level2 = new MethodCallNode("level2", 200);
        MethodCallNode level3 = new MethodCallNode("level3", 300);

        root.addChild(level1);
        level1.addChild(level2);
        level2.addChild(level3);

        assertEquals(0, root.getDepth());
        assertEquals(1, level1.getDepth());
        assertEquals(2, level2.getDepth());
        assertEquals(3, level3.getDepth());
    }

    @Test
    public void testArgsSetterGetter() {
        MethodCallNode node = new MethodCallNode("method", 0);
        Object[] args = new Object[] { "test", 123 };

        node.setArgs(args);
        assertArrayEquals(args, node.getArgs());
    }

    @Test
    public void testSnapshotIdSetterGetter() {
        MethodCallNode node = new MethodCallNode("method", 0);

        node.setSnapshotId("20260112-101010-001-00001");
        assertEquals("20260112-101010-001-00001", node.getSnapshotId());
    }

    @Test
    public void testComplexTree() {
        // Build a tree:
        // root
        // / \
        // A B
        // / \ \
        // C D E

        MethodCallNode root = new MethodCallNode("root", 0);
        MethodCallNode a = new MethodCallNode("A", 100);
        MethodCallNode b = new MethodCallNode("B", 200);
        MethodCallNode c = new MethodCallNode("C", 110);
        MethodCallNode d = new MethodCallNode("D", 120);
        MethodCallNode e = new MethodCallNode("E", 210);

        root.addChild(a);
        root.addChild(b);
        a.addChild(c);
        a.addChild(d);
        b.addChild(e);

        // Verify structure
        assertEquals(2, root.getChildren().size());
        assertEquals(2, a.getChildren().size());
        assertEquals(1, b.getChildren().size());
        assertEquals(0, c.getChildren().size());

        // Verify depths
        assertEquals(0, root.getDepth());
        assertEquals(1, a.getDepth());
        assertEquals(1, b.getDepth());
        assertEquals(2, c.getDepth());
        assertEquals(2, d.getDepth());
        assertEquals(2, e.getDepth());
    }
}
