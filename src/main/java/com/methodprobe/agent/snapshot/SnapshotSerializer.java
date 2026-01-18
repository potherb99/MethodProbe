package com.methodprobe.agent.snapshot;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayOutputStream;

/**
 * High-performance serializer using Kryo.
 * Uses ThreadLocal Kryo instances since Kryo is not thread-safe.
 */
public class SnapshotSerializer {

    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // Allow any class
        kryo.setReferences(true); // Handle circular references
        return kryo;
    });

    private static final int DEFAULT_BUFFER_SIZE = 4096;
    private static int maxObjectSize = 1048576; // 1MB default

    /**
     * Set maximum object size for serialization.
     */
    public static void setMaxObjectSize(int size) {
        maxObjectSize = size;
    }

    /**
     * Serialize a single object to bytes.
     * Returns null if serialization fails.
     */
    public static byte[] serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            Kryo kryo = KRYO.get();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
            try (Output output = new Output(baos, maxObjectSize)) {
                kryo.writeClassAndObject(output, obj);
            }
            byte[] result = baos.toByteArray();
            if (result.length > maxObjectSize) {
                // Truncate and mark as oversized
                System.err.println("[MethodProbe] Object too large, skipping: " + obj.getClass().getName());
                return null;
            }
            return result;
        } catch (Exception e) {
            System.err.println("[MethodProbe] Serialization failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Serialize multiple arguments.
     */
    public static byte[][] serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return new byte[0][];
        }
        byte[][] result = new byte[args.length][];
        for (int i = 0; i < args.length; i++) {
            result[i] = serialize(args[i]);
        }
        return result;
    }

    /**
     * Get argument types as strings.
     */
    public static String[] getArgTypes(Object[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        String[] types = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass().getName() : "null";
        }
        return types;
    }

    /**
     * Deserialize bytes back to object.
     */
    public static Object deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            Kryo kryo = KRYO.get();
            try (Input input = new Input(data)) {
                return kryo.readClassAndObject(input);
            }
        } catch (Exception e) {
            System.err.println("[MethodProbe] Deserialization failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserialize multiple arguments.
     */
    public static Object[] deserializeArgs(byte[][] data) {
        if (data == null || data.length == 0) {
            return new Object[0];
        }
        Object[] result = new Object[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = deserialize(data[i]);
        }
        return result;
    }
}
