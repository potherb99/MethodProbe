package com.methodprobe.agent.config;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for AgentConfig.
 */
public class AgentConfigTest {

    // Note: AgentConfig state persists between tests, so tests should
    // set explicit expectations rather than assuming a clean state.

    // ==== Flat Mode Tests ====

    @Test
    public void testSetFlatEnabled() {
        AgentConfig.setFlatEnabled(true);
        assertTrue(AgentConfig.isFlatEnabled());

        AgentConfig.setFlatEnabled(false);
        assertFalse(AgentConfig.isFlatEnabled());
    }

    @Test
    public void testFlatThreshold() {
        AgentConfig.setFlatThresholdMs(500);
        assertEquals(500, AgentConfig.getFlatThresholdMs());

        AgentConfig.setFlatThresholdMs(0);
        assertEquals(0, AgentConfig.getFlatThresholdMs());
    }

    @Test
    public void testAddFlatPackage() {
        AgentConfig.addFlatPackage("com.test.flatpkg");
        assertTrue(AgentConfig.getFlatPackages().contains("com.test.flatpkg"));
    }

    @Test
    public void testAddFlatClass() {
        AgentConfig.addFlatClass("com.test.FlatTestClass");
        assertTrue(AgentConfig.getFlatClasses().contains("com.test.FlatTestClass"));
    }

    @Test
    public void testAddFlatMethod() {
        AgentConfig.addFlatMethod("com.test.Service.flatMethod");
        assertTrue(AgentConfig.getFlatMethods().contains("com.test.Service.flatMethod"));
    }

    @Test
    public void testShouldLogFlatMethodByPackage() {
        AgentConfig.setFlatEnabled(true);
        AgentConfig.addFlatPackage("com.flattest");

        assertTrue(AgentConfig.shouldLogFlatMethod("com.flattest.Service", "doWork"));
        assertTrue(AgentConfig.shouldLogFlatMethod("com.flattest.sub.Dao", "query"));
    }

    @Test
    public void testShouldLogFlatMethodByMethod() {
        AgentConfig.setFlatEnabled(true);
        AgentConfig.addFlatMethod("com.methodtest.Service.specificMethod");

        assertTrue(AgentConfig.shouldLogFlatMethod("com.methodtest.Service", "specificMethod"));
        assertFalse(AgentConfig.shouldLogFlatMethod("com.methodtest.Service", "otherMethod"));
    }

    // ==== Tree Mode Tests ====

    @Test
    public void testSetTreeEnabled() {
        AgentConfig.setTreeEnabled(true);
        assertTrue(AgentConfig.isTreeEnabled());

        AgentConfig.setTreeEnabled(false);
        assertFalse(AgentConfig.isTreeEnabled());
    }

    @Test
    public void testTreeThreshold() {
        AgentConfig.setTreeThresholdMs(200);
        assertEquals(200, AgentConfig.getTreeThresholdMs());
    }

    @Test
    public void testAddTreeEntryMethod() {
        AgentConfig.addTreeEntryMethod("com.tree.Controller.handleRequest");
        assertTrue(AgentConfig.getTreeEntryMethods().contains("com.tree.Controller.handleRequest"));
    }

    @Test
    public void testAddTreePackage() {
        AgentConfig.addTreePackage("com.tree.pkg");
        assertTrue(AgentConfig.getTreePackages().contains("com.tree.pkg"));
    }

    @Test
    public void testIsTreeEntryMethod() {
        AgentConfig.addTreeEntryMethod("com.entry.Controller.handle");

        assertTrue(AgentConfig.isTreeEntryMethod("com.entry.Controller", "handle"));
        assertFalse(AgentConfig.isTreeEntryMethod("com.entry.Controller", "otherMethod"));
        assertFalse(AgentConfig.isTreeEntryMethod("com.other.Controller", "handle"));
    }

    @Test
    public void testShouldIncludeInTree() {
        AgentConfig.addTreePackage("com.treeinc");

        assertTrue(AgentConfig.shouldIncludeInTree("com.treeinc.Service"));
        assertTrue(AgentConfig.shouldIncludeInTree("com.treeinc.sub.Dao"));
    }

    // ==== Snapshot Config Tests ====

    @Test
    public void testSnapshotEnabled() {
        AgentConfig.snapshotEnabled = true;
        assertTrue(AgentConfig.snapshotEnabled);

        AgentConfig.snapshotEnabled = false;
        assertFalse(AgentConfig.snapshotEnabled);
    }

    @Test
    public void testSnapshotSerializeSync() {
        AgentConfig.snapshotSerializeSync = true;
        assertTrue(AgentConfig.snapshotSerializeSync);

        AgentConfig.snapshotSerializeSync = false;
        assertFalse(AgentConfig.snapshotSerializeSync);
    }

    @Test
    public void testTreeSnapshotProbeAll() {
        AgentConfig.treeSnapshotProbeAll = true;
        assertTrue(AgentConfig.treeSnapshotProbeAll);

        AgentConfig.treeSnapshotProbeAll = false;
        assertFalse(AgentConfig.treeSnapshotProbeAll);
    }

    // ==== Instrumentation Decision Tests ====

    @Test
    public void testShouldInstrumentClassForFlat() {
        AgentConfig.setFlatEnabled(true);
        AgentConfig.addFlatPackage("com.instrflat");

        assertTrue(AgentConfig.shouldInstrumentForFlat("com.instrflat.Service"));
        assertFalse(AgentConfig.shouldInstrumentForFlat("com.otherflat.Service"));
    }

    @Test
    public void testShouldInstrumentClassForTree() {
        AgentConfig.setTreeEnabled(true);
        AgentConfig.addTreePackage("com.instrtree");
        AgentConfig.addTreeEntryMethod("com.instrtree.Entry.start");

        assertTrue(AgentConfig.shouldInstrumentForTree("com.instrtree.Service"));
        assertTrue(AgentConfig.shouldInstrumentForTree("com.instrtree.Entry"));
    }

    @Test
    public void testShouldInstrumentClassCombined() {
        AgentConfig.setFlatEnabled(true);
        AgentConfig.setTreeEnabled(true);
        AgentConfig.addFlatPackage("com.combflat");
        AgentConfig.addTreePackage("com.combtree");

        assertTrue(AgentConfig.shouldInstrumentClass("com.combflat.Service"));
        assertTrue(AgentConfig.shouldInstrumentClass("com.combtree.Service"));
    }

    // ==== Exclusion Tests (via shouldInstrumentClass) ====

    @Test
    public void testSystemClassesNotInstrumented() {
        // Ensure init is called to set up default exclusions
        AgentConfig.init(null);

        AgentConfig.setFlatEnabled(true);
        // Even if we add java package, system classes should be excluded
        AgentConfig.addFlatPackage("java.lang");

        // shouldInstrumentClass checks exclusion internally
        assertFalse("java.lang.String should be excluded",
                AgentConfig.shouldInstrumentClass("java.lang.String"));
    }

    @Test
    public void testUserClassesCanBeInstrumented() {
        AgentConfig.setFlatEnabled(true);
        AgentConfig.addFlatPackage("com.userapp");

        assertTrue("User classes should be instrumentable",
                AgentConfig.shouldInstrumentClass("com.userapp.Service"));
    }
}
