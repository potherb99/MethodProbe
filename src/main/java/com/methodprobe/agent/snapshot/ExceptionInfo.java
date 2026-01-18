package com.methodprobe.agent.snapshot;

import java.io.Serializable;

/**
 * Serializable exception information for snapshot storage.
 * Instead of serializing the entire Throwable (which may fail due to internal
 * JDK classes),
 * we capture the essential exception details as simple strings.
 */
public class ExceptionInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String exceptionClass;
    private String message;
    private String stackTrace;

    public ExceptionInfo() {
    }

    public ExceptionInfo(Throwable throwable) {
        this(throwable, 10); // Default depth
    }

    /**
     * Create ExceptionInfo with limited stack trace depth.
     * 
     * @param throwable the exception
     * @param maxDepth  maximum number of stack frames to capture
     */
    public ExceptionInfo(Throwable throwable, int maxDepth) {
        if (throwable != null) {
            this.exceptionClass = throwable.getClass().getName();
            this.message = throwable.getMessage();

            // Build stack trace with depth limit (more efficient than printStackTrace)
            StringBuilder sb = new StringBuilder();
            sb.append(throwable.getClass().getName());
            if (throwable.getMessage() != null) {
                sb.append(": ").append(throwable.getMessage());
            }
            sb.append("\n");

            StackTraceElement[] elements = throwable.getStackTrace();
            int depth = Math.min(elements.length, maxDepth);
            for (int i = 0; i < depth; i++) {
                sb.append("\tat ").append(elements[i].toString()).append("\n");
            }
            if (elements.length > maxDepth) {
                sb.append("\t... ").append(elements.length - maxDepth).append(" more\n");
            }

            // Include cause if present (limited)
            Throwable cause = throwable.getCause();
            if (cause != null && cause != throwable) {
                sb.append("Caused by: ").append(cause.getClass().getName());
                if (cause.getMessage() != null) {
                    sb.append(": ").append(cause.getMessage());
                }
                sb.append("\n");
                StackTraceElement[] causeElements = cause.getStackTrace();
                int causeDepth = Math.min(causeElements.length, 3);
                for (int i = 0; i < causeDepth; i++) {
                    sb.append("\tat ").append(causeElements[i].toString()).append("\n");
                }
                if (causeElements.length > causeDepth) {
                    sb.append("\t... ").append(causeElements.length - causeDepth).append(" more\n");
                }
            }

            this.stackTrace = sb.toString();
        }
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getSimpleClassName() {
        if (exceptionClass == null)
            return null;
        int lastDot = exceptionClass.lastIndexOf('.');
        return lastDot >= 0 ? exceptionClass.substring(lastDot + 1) : exceptionClass;
    }

    @Override
    public String toString() {
        if (message != null) {
            return exceptionClass + ": " + message;
        }
        return exceptionClass;
    }
}
