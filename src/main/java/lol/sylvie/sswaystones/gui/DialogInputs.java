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
 * Builders for the native-dialog input controls and the {@link CommandTemplate} action that
 * submits their values into a backend command via {@code $(key)} substitution.
 *
 * <p>
 * The dialog system has no boolean/checkbox control, so booleans are modeled as a two-entry
 * {@link SingleOptionInput} ("On"/"Off") whose selected entry id ("true"/"false") is substituted
 * into the command.
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
        // On=green / Off=red display text, matching the sgui menu's toggle colors; the submitted
        // ids stay plain "true"/"false".
        List<SingleOptionInput.Entry> entries = List.of(
                new SingleOptionInput.Entry("true",
                        Optional.of(Component.literal("On").withStyle(ChatFormatting.GREEN)), initial),
                new SingleOptionInput.Entry("false",
                        Optional.of(Component.literal("Off").withStyle(ChatFormatting.RED)), !initial));
        return new Input(key, new SingleOptionInput(width, entries, Component.literal(label), true));
    }

    /** One selector entry: submitted id, its colored display text, and whether it starts selected. */
    public static SingleOptionInput.Entry entry(String id, String display, ChatFormatting color, boolean initial) {
        return new SingleOptionInput.Entry(id, Optional.of(Component.literal(display).withStyle(color)), initial);
    }

    /**
     * A single-option (cycling) picker bound to {@code key} built from pre-made {@link #entry}
     * entries. The submitted value is the selected entry's id.
     */
    public static Input singleOption(String key, String label, int width, List<SingleOptionInput.Entry> entries) {
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
