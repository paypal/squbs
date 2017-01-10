package org.squbs.testkit.japi;


import static org.junit.Assert.*;
import org.junit.Test;

public class AbstractCustomTestKitNoClassPathTest extends AbstractCustomTestKit {

    public AbstractCustomTestKitNoClassPathTest() {
        super(false);
    }

    @Test
    public void testDefaultListenerStarted() {
        assertEquals(port("default-listener"), port());
    }

    @Test
    public void testActorSystemName() {
        assertTrue(system().name().matches("org-squbs-testkit-japi-AbstractCustomTestKitNoClassPathTest-\\d+"));
    }

    @Test
    public void testDefaultResources() {
        assertEquals(1, boot().cubes().size());
        assertEquals("CustomTestKitDefaultSpec", boot().cubes().head().info().name());
    }
}
