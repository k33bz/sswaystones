/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The {@code settings_ui} flag: only an explicit, case-insensitive "dialog"
 * opts into the dialog UI; everything else (null, empty, typos, the default)
 * stays sgui.
 */
class SettingsUiTest {

    @Test
    void defaultConfigValueIsSgui() {
        assertEquals("sgui", new Configuration.Instance().settingsUi);
        assertFalse(Configuration.isDialogUi("sgui"));
    }

    @Test
    void dialogValueSelectsDialogCaseInsensitively() {
        assertTrue(Configuration.isDialogUi("dialog"));
        assertTrue(Configuration.isDialogUi("DIALOG"));
        assertTrue(Configuration.isDialogUi("  Dialog  "));
    }

    @Test
    void unknownOrNullFallsBackToSgui() {
        assertFalse(Configuration.isDialogUi(null));
        assertFalse(Configuration.isDialogUi(""));
        assertFalse(Configuration.isDialogUi("chest"));
        assertFalse(Configuration.isDialogUi("dialogue")); // typo != dialog
    }
}
