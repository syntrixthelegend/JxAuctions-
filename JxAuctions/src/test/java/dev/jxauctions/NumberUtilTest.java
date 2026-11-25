package dev.jxauctions;

import dev.jxauctions.util.NumberUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilTest {

    @Test
    void parsesAbbreviationsAndRejectsInfinity() {
        assertEquals(1500.0, NumberUtil.parseAmount("1.5k"));
        assertEquals(2_000_000.0, NumberUtil.parseAmount("2m"));
        assertEquals(1_000_000_000.0, NumberUtil.parseAmount("1b"));
        assertEquals(-1.0, NumberUtil.parseAmount("1e308m"));
        assertEquals(-1.0, NumberUtil.parseAmount("Infinity"));
        assertEquals(-1.0, NumberUtil.parseAmount("-50"));
        assertEquals(-1.0, NumberUtil.parseAmount("0"));
    }

    @Test
    void formatsNumbersInAllModes() {
        assertEquals("1,250.5", NumberUtil.format(1250.5, 0, "#,###.##"));
        assertEquals("1.5K", NumberUtil.format(1500, 1, "#,###.##"));
        assertEquals("1500", NumberUtil.format(1500, 2, "#,###.##"));
    }
}
