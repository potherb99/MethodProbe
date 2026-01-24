package com.methodprobe.agent.snapshot;

import java.io.Serializable;

/**
 * Fallback representation for objects that cannot be serialized.
 * Used when Kryo serialization fails for complex objects (e.g., Spring CGLIB
 * proxies).
 */
public class SerializationMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String className;
    private final String stringRepresentation;
    private final String errorType;

    public SerializationMetadata(String className, String stringRepresentation, String errorType) {
        this.className = className;
        this.stringRepresentation = stringRepresentation;
        this.errorType = errorType;
    }

    public String getClassName() {
        return className;
    }

    public String getStringRepresentation() {
        return stringRepresentation;
    }

    public String getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return "[" + errorType + "] " + className + ": " + stringRepresentation;
    }
}
