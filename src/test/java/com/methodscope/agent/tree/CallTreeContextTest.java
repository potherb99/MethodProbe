package com.methodprobe.agent.tree;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.methodprobe.agent.config.AgentConfig;

/**
 * Tests for CallTreeContext.
 */
public class CallTreeContextTest {

    @Before
    public void setUp() {
        // Clear any existing context
        CallTreeContext.clear();

        // Reset AgentConfig for testing
        AgentConfig.setTreeEnabled(true);
        AgentConfig.addTreeEntryMethod("com.example.Controller.handleRequest");
        AgentConfig.addTreePackage("com.example");
        AgentConfig.snapshotEnabled = false;
    }

    @After
    public void tearDown() {
        CallTreeContext.clear();
    }

    @Test
    public void testIsInTreeInitiallyFalse() {
        assertFalse("Should not be in tree initially", CallTreeContext.isInTree());
    }

    @Test
    public void testOnMethodEnterEntryMethod() {
        boolean result = CallTreeContext.onMethodEnter(
                "com.example.Controller", "handleRequest", new Object[0]);

        assertTrue("Entry method should return true", result);
        assertTrue("Should be in tree after entry", CallTreeContext.isInTree());
    }

    @Test
    public void testOnMethodEnterNonEntryMethod() {
        // Without entering an entry method first
        boolean result = CallTreeContext.onMethodEnter(
                "com.example.Service", "doWork", new Object[0]);

        assertFalse("Non-entry method without tree should return false", result);
        assertFalse("Should not be in tree", CallTreeContext.isInTree());
    }

    @Test
    public void testOnMethodEnterNestedMethod() {
        // Enter entry method first
        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);

        // Now enter a nested method
        boolean result = CallTreeContext.onMethodEnter(
                "com.example.Service", "doWork", new Object[0]);

        assertTrue("Nested method in same package should return true", result);
        assertEquals(2, CallTreeContext.getCurrentDepth());
    }

    @Test
    public void testOnMethodExitClearsContext() {
        // Enter and exit entry method
        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);
        assertTrue(CallTreeContext.isInTree());

        CallTreeContext.onMethodExit("com.example.Controller", "handleRequest", null);

        // Context should be cleared after entry method exits
        assertFalse("Context should be cleared after entry exits", CallTreeContext.isInTree());
    }

    @Test
    public void testGetCurrentDepth() {
        assertEquals(0, CallTreeContext.getCurrentDepth());

        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);
        assertEquals(1, CallTreeContext.getCurrentDepth());

        CallTreeContext.onMethodEnter("com.example.Service", "method1", new Object[0]);
        assertEquals(2, CallTreeContext.getCurrentDepth());

        CallTreeContext.onMethodEnter("com.example.Dao", "query", new Object[0]);
        assertEquals(3, CallTreeContext.getCurrentDepth());
    }

    @Test
    public void testClear() {
        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);
        CallTreeContext.onMethodEnter("com.example.Service", "doWork", new Object[0]);

        assertTrue(CallTreeContext.isInTree());
        assertTrue(CallTreeContext.getCurrentDepth() > 0);

        CallTreeContext.clear();

        assertFalse(CallTreeContext.isInTree());
        assertEquals(0, CallTreeContext.getCurrentDepth());
    }

    @Test
    public void testTreeModeDisabled() {
        AgentConfig.setTreeEnabled(false);

        boolean result = CallTreeContext.onMethodEnter(
                "com.example.Controller", "handleRequest", new Object[0]);

        assertFalse("Should return false when tree mode disabled", result);
        assertFalse(CallTreeContext.isInTree());
    }

    @Test
    public void testMethodOutsidePackage() {
        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);

        // Method outside configured package
        boolean result = CallTreeContext.onMethodEnter(
                "com.other.Service", "external", new Object[0]);

        assertFalse("Method outside package should return false", result);
        assertEquals(1, CallTreeContext.getCurrentDepth()); // Still only entry method
    }

    @Test
    public void testNestedCallSequence() {
        // Simulate: handleRequest -> loadData -> query -> (exit query) -> (exit
        // loadData)

        CallTreeContext.onMethodEnter("com.example.Controller", "handleRequest", new Object[0]);
        CallTreeContext.onMethodEnter("com.example.Service", "loadData", new Object[0]);
        CallTreeContext.onMethodEnter("com.example.Dao", "query", new Object[0]);
        assertEquals(3, CallTreeContext.getCurrentDepth());

        // Exit query
        CallTreeContext.onMethodExit("com.example.Dao", "query", null);
        assertEquals(2, CallTreeContext.getCurrentDepth());

        // Exit loadData
        CallTreeContext.onMethodExit("com.example.Service", "loadData", null);
        assertEquals(1, CallTreeContext.getCurrentDepth());

        // Still in tree (entry method not exited)
        assertTrue(CallTreeContext.isInTree());
    }
}
