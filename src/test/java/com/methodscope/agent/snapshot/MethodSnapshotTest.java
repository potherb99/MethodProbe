package com.methodprobe.agent.snapshot;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for MethodSnapshot.
 */
public class MethodSnapshotTest {

    @Test
    public void testDefaultConstructor() {
        MethodSnapshot snapshot = new MethodSnapshot();
        assertNull(snapshot.getSnapshotId());
        assertNull(snapshot.getClassName());
        assertNull(snapshot.getMethodName());
        assertNull(snapshot.getThreadName());
        assertEquals(0, snapshot.getTimestamp());
        assertEquals(0.0, snapshot.getDurationMs(), 0.001);
    }

    @Test
    public void testParameterizedConstructor() {
        long timestamp = System.currentTimeMillis();
        MethodSnapshot snapshot = new MethodSnapshot(
                "snap-001", timestamp, "com.example.Service",
                "doSomething", "main", 123.45);

        assertEquals("snap-001", snapshot.getSnapshotId());
        assertEquals(timestamp, snapshot.getTimestamp());
        assertEquals("com.example.Service", snapshot.getClassName());
        assertEquals("doSomething", snapshot.getMethodName());
        assertEquals("main", snapshot.getThreadName());
        assertEquals(123.45, snapshot.getDurationMs(), 0.001);
    }

    @Test
    public void testSettersAndGetters() {
        MethodSnapshot snapshot = new MethodSnapshot();

        snapshot.setSnapshotId("snap-002");
        snapshot.setTimestamp(1234567890L);
        snapshot.setClassName("com.test.Clazz");
        snapshot.setMethodName("testMethod");
        snapshot.setThreadName("worker-1");
        snapshot.setDurationMs(99.99);

        assertEquals("snap-002", snapshot.getSnapshotId());
        assertEquals(1234567890L, snapshot.getTimestamp());
        assertEquals("com.test.Clazz", snapshot.getClassName());
        assertEquals("testMethod", snapshot.getMethodName());
        assertEquals("worker-1", snapshot.getThreadName());
        assertEquals(99.99, snapshot.getDurationMs(), 0.001);
    }

    @Test
    public void testArgTypesSetterGetter() {
        MethodSnapshot snapshot = new MethodSnapshot();
        String[] types = new String[] { "java.lang.String", "int" };

        snapshot.setArgTypes(types);

        assertArrayEquals(types, snapshot.getArgTypes());
    }

    @Test
    public void testSerializedArgsSetterGetter() {
        MethodSnapshot snapshot = new MethodSnapshot();
        byte[][] args = new byte[][] {
                new byte[] { 1, 2, 3 },
                new byte[] { 4, 5 }
        };

        snapshot.setSerializedArgs(args);

        assertArrayEquals(args, snapshot.getSerializedArgs());
    }

    @Test
    public void testSerializedExceptionSetterGetter() {
        MethodSnapshot snapshot = new MethodSnapshot();
        byte[] exception = new byte[] { 1, 2, 3, 4, 5 };

        snapshot.setSerializedException(exception);

        assertArrayEquals(exception, snapshot.getSerializedException());
    }

    @Test
    public void testGetFullMethodName() {
        MethodSnapshot snapshot = new MethodSnapshot(
                "id", System.currentTimeMillis(),
                "com.example.Service", "process", "main", 10.0);

        assertEquals("com.example.Service.process", snapshot.getFullMethodName());
    }

    @Test
    public void testGetFullMethodNameWithNulls() {
        MethodSnapshot snapshot = new MethodSnapshot();
        // Should not throw NPE
        String fullName = snapshot.getFullMethodName();
        assertEquals("null.null", fullName);
    }
}
