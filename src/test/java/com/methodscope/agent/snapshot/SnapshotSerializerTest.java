package com.methodprobe.agent.snapshot;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.Serializable;
import java.util.*;

/**
 * Tests for SnapshotSerializer.
 */
public class SnapshotSerializerTest {

    @Test
    public void testSerializeNull() {
        byte[] result = SnapshotSerializer.serialize(null);
        assertNull("Serializing null should return null", result);
    }

    @Test
    public void testSerializeDeserializeString() {
        String original = "Hello, World!";
        byte[] bytes = SnapshotSerializer.serialize(original);

        assertNotNull("Serialized bytes should not be null", bytes);
        assertTrue("Serialized bytes should have content", bytes.length > 0);

        Object deserialized = SnapshotSerializer.deserialize(bytes);
        assertEquals("Deserialized should equal original", original, deserialized);
    }

    @Test
    public void testSerializeDeserializeInteger() {
        Integer original = 42;
        byte[] bytes = SnapshotSerializer.serialize(original);
        Object deserialized = SnapshotSerializer.deserialize(bytes);

        assertEquals("Deserialized Integer should match", original, deserialized);
    }

    @Test
    public void testSerializeDeserializeList() {
        List<String> original = Arrays.asList("a", "b", "c");
        byte[] bytes = SnapshotSerializer.serialize(original);
        Object deserialized = SnapshotSerializer.deserialize(bytes);

        assertEquals("Deserialized List should match", original, deserialized);
    }

    @Test
    public void testSerializeDeserializeMap() {
        Map<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        byte[] bytes = SnapshotSerializer.serialize(original);
        Object deserialized = SnapshotSerializer.deserialize(bytes);

        assertEquals("Deserialized Map should match", original, deserialized);
    }

    @Test
    public void testSerializeArgsNull() {
        // Implementation returns empty array for null args
        byte[][] result = SnapshotSerializer.serializeArgs(null);
        assertNotNull(result);
        assertEquals("serializeArgs(null) should return empty array", 0, result.length);
    }

    @Test
    public void testSerializeArgsEmpty() {
        byte[][] result = SnapshotSerializer.serializeArgs(new Object[0]);
        assertNotNull("serializeArgs([]) should not return null", result);
        assertEquals("Should have 0 elements", 0, result.length);
    }

    @Test
    public void testSerializeArgsMixed() {
        Object[] args = new Object[] { "hello", 123, null, true };
        byte[][] serialized = SnapshotSerializer.serializeArgs(args);

        assertEquals("Should serialize all args", 4, serialized.length);
        assertNotNull("String arg should serialize", serialized[0]);
        assertNotNull("Integer arg should serialize", serialized[1]);
        assertNull("null arg should be null", serialized[2]);
        assertNotNull("Boolean arg should serialize", serialized[3]);

        // Deserialize and verify
        assertEquals("hello", SnapshotSerializer.deserialize(serialized[0]));
        assertEquals(123, SnapshotSerializer.deserialize(serialized[1]));
        assertEquals(true, SnapshotSerializer.deserialize(serialized[3]));
    }

    @Test
    public void testGetArgTypesNull() {
        String[] types = SnapshotSerializer.getArgTypes(null);
        assertNotNull(types);
        assertEquals(0, types.length);
    }

    @Test
    public void testGetArgTypesEmpty() {
        String[] types = SnapshotSerializer.getArgTypes(new Object[0]);
        assertNotNull(types);
        assertEquals(0, types.length);
    }

    @Test
    public void testGetArgTypesMixed() {
        Object[] args = new Object[] { "hello", 123, null, new ArrayList<>() };
        String[] types = SnapshotSerializer.getArgTypes(args);

        assertEquals(4, types.length);
        assertEquals("java.lang.String", types[0]);
        assertEquals("java.lang.Integer", types[1]);
        assertEquals("null", types[2]);
        assertEquals("java.util.ArrayList", types[3]);
    }

    @Test
    public void testDeserializeNull() {
        Object result = SnapshotSerializer.deserialize(null);
        assertNull("deserialize(null) should return null", result);
    }

    @Test
    public void testDeserializeEmpty() {
        Object result = SnapshotSerializer.deserialize(new byte[0]);
        assertNull("deserialize([]) should return null", result);
    }

    @Test
    public void testSerializeComplexTypes() {
        // Test with array
        int[] intArray = { 1, 2, 3 };
        byte[] bytes = SnapshotSerializer.serialize(intArray);
        assertNotNull("Should serialize int array", bytes);

        int[] deserializedArray = (int[]) SnapshotSerializer.deserialize(bytes);
        assertArrayEquals(intArray, deserializedArray);
    }
}
