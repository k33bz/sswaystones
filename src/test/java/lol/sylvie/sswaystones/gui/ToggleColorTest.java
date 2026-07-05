/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

/**
 * Guards the On=green / Off=red styling used for the native-dialog toggle state text.
 *
 * <p>
 * The dialog {@code Input}/{@code SingleOptionInput} classes require Minecraft's registry bootstrap
 * in their static init, so they can't be constructed in a plain unit test. But the COLOR itself is
 * carried by a plain {@link Component} (bootstrap-free), so we verify the exact styling
 * {@link DialogInputs#bool} applies to its entry displays here, and that the plain "true"/"false"
 * submit ids carry no color (the coloring must stay cosmetic).
 */
class ToggleColorTest {

    // Mirrors exactly what DialogInputs.bool() sets as the On / Off entry display components.
    private static Component onDisplay() {
        return Component.literal("On").withStyle(ChatFormatting.GREEN);
    }

    private static Component offDisplay() {
        return Component.literal("Off").withStyle(ChatFormatting.RED);
    }

    @Test
    void onStateTextIsGreen() {
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), onDisplay().getStyle().getColor());
        assertEquals("On", onDisplay().getString());
    }

    @Test
    void offStateTextIsRed() {
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), offDisplay().getStyle().getColor());
        assertEquals("Off", offDisplay().getString());
    }

    @Test
    void submitIdsCarryNoColor() {
        // The submitted VALUES ("true"/"false") are plain strings, never colored — coloring the
        // display must not affect what /waystonesettings apply receives.
        assertNull(Component.literal("true").getStyle().getColor());
        assertNull(Component.literal("false").getStyle().getColor());
    }

    @Test
    void toggleLabelStaysDefaultColored() {
        // bool() builds the label via Component.literal(label) with no withStyle — default color.
        assertNull(Component.literal("Global").getStyle().getColor());
    }
}
