/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

import static lol.sylvie.sswaystones.util.WaystoneNames.MAX_LENGTH;
import static lol.sylvie.sswaystones.util.WaystoneNames.sanitize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// What a player types is what everyone sees: no smuggled formatting, no line breaks, no overlong names
class WaystoneNamesTest {
    @Test
    void plainNamesAreUntouched() {
        assertEquals("Spawn Hub", sanitize("Spawn Hub"));
        assertEquals("Ünïcødé 村 ✦", sanitize("Ünïcødé 村 ✦"));
    }

    @Test
    void formattingCodesAreRemovedWithTheirCodeCharacter() {
        assertEquals("Fake Admin Stone", sanitize("§c§lFake §kAdmin§r Stone"));
        assertEquals("trailing", sanitize("trailing§"));
    }

    @Test
    void controlCharactersAreRemoved() {
        assertEquals("line oneline two", sanitize("line one\nline two"));
        assertEquals("tabbed", sanitize("tab\tbed"));
    }

    @Test
    void lengthIsCappedAfterCleaning() {
        String formatted = "§a".repeat(40) + "x".repeat(40);
        assertEquals("x".repeat(MAX_LENGTH), sanitize(formatted));
        assertEquals(MAX_LENGTH, sanitize("y".repeat(100)).length());
    }

    @Test
    void capNeverSplitsASurrogatePair() {
        String name = "a".repeat(MAX_LENGTH - 1) + "😀";
        assertEquals("a".repeat(MAX_LENGTH - 1), sanitize(name));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", sanitize(null));
    }
}
