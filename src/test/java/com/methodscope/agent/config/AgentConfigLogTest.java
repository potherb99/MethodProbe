package com.methodprobe.agent.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for AgentConfig log output configuration.
 */
public class AgentConfigLogTest {

    @Test
    public void testDefaultOutputMode() {
        // Default should be console
        String mode = AgentConfig.getOutputMode();
        assertEquals("console", mode);
    }

    @Test
    public void testSetOutputMode() {
        String originalMode = AgentConfig.getOutputMode();
        try {
            AgentConfig.setOutputMode("file");
            assertEquals("file", AgentConfig.getOutputMode());

            AgentConfig.setOutputMode("console");
            assertEquals("console", AgentConfig.getOutputMode());
        } finally {
            AgentConfig.setOutputMode(originalMode);
        }
    }

    @Test
    public void testDefaultOutputDir() {
        String dir = AgentConfig.getOutputDir();
        assertEquals("./probe-logs", dir);
    }

    @Test
    public void testSetOutputDir() {
        String originalDir = AgentConfig.getOutputDir();
        try {
            AgentConfig.setOutputDir("/custom/path");
            assertEquals("/custom/path", AgentConfig.getOutputDir());
        } finally {
            AgentConfig.setOutputDir(originalDir);
        }
    }

    @Test
    public void testDefaultBufferSize() {
        int bufferSize = AgentConfig.getOutputBufferSize();
        assertEquals(10000, bufferSize);
    }

    @Test
    public void testDefaultFlushInterval() {
        long flushInterval = AgentConfig.getOutputFlushInterval();
        assertEquals(1000L, flushInterval);
    }
}
