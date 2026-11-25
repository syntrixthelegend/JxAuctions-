package dev.jxauctions;

import dev.jxauctions.util.UpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {

    @Test
    void comparesSemVerProperly() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.1", "2.0.0"));
        assertTrue(UpdateChecker.isNewerVersion("2.1.0", "2.0.9"));
        assertFalse(UpdateChecker.isNewerVersion("2.0.0", "2.0.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.9.9", "2.0.0"));
    }
}
