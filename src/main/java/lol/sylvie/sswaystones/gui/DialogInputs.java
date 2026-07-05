/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;

/**
 * Shared builders for the 26.x native-dialog INPUT controls (text fields and single-option
 * on/off pickers) and the {@link CommandTemplate} action that submits their values into a
 * permission-0 backend command via {@code $(key)} substitution.
 *
 * <p>
 * The native Dialog system has no boolean/checkbox control, so booleans are modeled as a
 * two-entry {@link SingleOptionInput} ("On"/"Off") whose selected entry id ("true"/"false") is
 * substituted into the backend command. This mirrors how the Bedrock cumulus form's
 * {@code .toggle()} values are read back in one submit handler.
 */
public final class DialogInputs {
    private DialogInputs() {
    }

    /** A single-line text input bound to {@code key}, for later {@code $(key)} substitution. */
    public static Input text(String key, String label, String initial, int maxLength, int width) {
        return new Input(key, new TextInput(width, Component.literal(label), true,
                initial == null ? "" : initial, maxLength, Optional.empty()));
    }

    /**
     * A boolean modeled as an On/Off single-option picker bound to {@code key}. The submitted value
     * is the string "true" or "false"; {@code initial} pre-selects the matching entry.
     */
    public static Input bool(String key, String label, boolean initial, int width) {
        // Color the STATE text (only the On/Off entry display, not the label) green/red so it reads
        // like the old sgui menu's colored toggles. Uniform across every toggle — On=green, Off=red
        // (Hide Name is NOT inverted). Cosmetic: the submitted id ("true"/"false") is unchanged.
        List<SingleOptionInput.Entry> entries = List.of(
                new SingleOptionInput.Entry("true",
                        Optional.of(Component.literal("On").withStyle(ChatFormatting.GREEN)), initial),
                new SingleOptionInput.Entry("false",
                        Optional.of(Component.literal("Off").withStyle(ChatFormatting.RED)), !initial));
        return new Input(key, new SingleOptionInput(width, entries, Component.literal(label), true));
    }

    /**
     * A submit action that runs {@code template} with the dialog's input values substituted in
     * ({@code $(key)} placeholders). {@link ParsedTemplate} has no public constructor, so we decode
     * one through its string codec — the same path the vanilla dialog loader uses.
     */
    public static Optional<Action> command(String template) {
        ParsedTemplate parsed = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template))
                .getOrThrow(msg -> new IllegalArgumentException("bad dialog command template: " + msg));
        return Optional.of(new CommandTemplate(parsed));
    }
}
