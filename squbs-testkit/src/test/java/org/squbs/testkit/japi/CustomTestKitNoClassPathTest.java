package org.squbs.testkit.japi;


import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

public class CustomTestKitNoClassPathTest extends CustomTestKit {

    public CustomTestKitNoClassPathTest() {
        super(false);
    }

    @After
    public void tearDown() {
        shutdown();
    }

    @Test
    public void testDefaultListenerStarted() {
        assertEquals(port("default-listener"), port());
    }

    @Test
    public void testActorSystemName() {
        assertTrue(system().name().matches("org-squbs-testkit-japi-CustomTestKitNoClassPathTest-\\d+"));
    }

    @Test
    public void testDefaultResources() {
        assertEquals(1, boot().cubes().size());
        assertEquals("CustomTestKitDefaultSpec", boot().cubes().head().info().name());
    }
}
