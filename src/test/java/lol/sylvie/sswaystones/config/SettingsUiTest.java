/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.sylvie.sswaystones.config.Configuration.SettingsUi;
import org.junit.jupiter.api.Test;

/**
 * The config flag selecting the sgui vs native-dialog settings path. The
 * DEFAULT must stay sgui (non-breaking): only an explicit, case-insensitive
 * "dialog" opts into the new UI; everything else (null, empty, typos, the
 * literal default) resolves to sgui.
 */
class SettingsUiTest {

    @Test
    void defaultConfigValueIsSgui() {
        // the field default on a fresh Instance
        assertEquals("sgui", new Configuration.Instance().settingsUi);
        assertEquals(SettingsUi.SGUI, SettingsUi.fromConfig("sgui"));
        assertFalse(SettingsUi.fromConfig("sgui").isDialog());
    }

    @Test
    void dialogValueSelectsDialogCaseInsensitively() {
        assertEquals(SettingsUi.DIALOG, SettingsUi.fromConfig("dialog"));
        assertEquals(SettingsUi.DIALOG, SettingsUi.fromConfig("DIALOG"));
        assertEquals(SettingsUi.DIALOG, SettingsUi.fromConfig("  Dialog  "));
        assertTrue(SettingsUi.fromConfig("dialog").isDialog());
    }

    @Test
    void unknownOrNullFallsBackToSgui() {
        assertEquals(SettingsUi.SGUI, SettingsUi.fromConfig(null));
        assertEquals(SettingsUi.SGUI, SettingsUi.fromConfig(""));
        assertEquals(SettingsUi.SGUI, SettingsUi.fromConfig("chest"));
        assertEquals(SettingsUi.SGUI, SettingsUi.fromConfig("dialogue")); // typo != dialog
    }
}
