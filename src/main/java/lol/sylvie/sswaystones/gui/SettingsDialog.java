/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.util.WaystoneColors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

// The "dialog" settings_ui: name, access and hide-name in one native dialog. A
// dialog has no server callbacks, so Done runs /waystonesettings apply with the
// inputs filled in, and the permissions are checked again there.
public final class SettingsDialog {
    private static final int INPUT_WIDTH = 140;
    private static final int BUTTON_WIDTH = 100;

    private SettingsDialog() {
    }

    public static void open(ServerPlayer player, WaystoneRecord waystone) {
        WaystoneRecord.AccessSettings access = waystone.getAccessSettings();
        AccessMode.Permissions perms = ViewerUtil.permissions(player);

        List<DialogBody> body = List.of(new PlainMessage(
                Component.translatable("gui.sswaystones.dialog_access_help").withStyle(ChatFormatting.GRAY), 200));

        AccessMode currentMode = AccessMode.fromSettings(access.isServerOwned(), access.isGlobal(), access.hasTeam());
        List<SingleOptionInput.Entry> accessEntries = new ArrayList<>();
        for (AccessMode mode : AccessMode.availableModes(currentMode, perms))
            accessEntries.add(entry(mode.id(), modeLabel(mode), WaystoneColors.modeColor(mode), mode == currentMode));

        List<Input> inputs = new ArrayList<>();
        inputs.add(text("name", label("gui.sswaystones.change_name"), waystone.getWaystoneName(), 32));
        inputs.add(singleOption("access", label("gui.sswaystones.dialog_access_label"), accessEntries));
        inputs.add(bool("hidename", label("gui.sswaystones.toggle_hide_name"), access.isNameHidden()));

        // name goes last so it may contain spaces and colons
        String template = "waystonesettings apply " + waystone.getHash()
                + " access:$(access) hidename:$(hidename) name:$(name)";

        List<ActionButton> buttons = List.of(
                new ActionButton(new CommonButtonData(Component.translatable("gui.sswaystones.dialog_done"),
                        Optional.of(Component.translatable("gui.sswaystones.dialog_done_tooltip")), BUTTON_WIDTH),
                        command(template)),
                new ActionButton(
                        new CommonButtonData(Component.translatable("gui.sswaystones.dialog_cancel"), BUTTON_WIDTH),
                        Optional.empty()));

        CommonDialogData common = new CommonDialogData(Component.translatable("gui.sswaystones.access_settings"),
                Optional.empty(), true, false, DialogAction.CLOSE, body, inputs);

        Dialog dialog = new MultiActionDialog(common, buttons, Optional.empty(), 2);
        player.openDialog(Holder.direct(dialog));
    }

    // Input labels are plain strings
    private static String label(String translationKey) {
        return Component.translatable(translationKey).getString();
    }

    private static String modeLabel(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> label("gui.sswaystones.access_private");
            case TEAM -> label("gui.sswaystones.access_team");
            case GLOBAL -> label("gui.sswaystones.access_global");
            case SERVER -> label("gui.sswaystones.access_server");
        };
    }

    private static Input text(String key, String label, String initial, int maxLength) {
        return new Input(key, new TextInput(INPUT_WIDTH, Component.literal(label), true, initial == null ? "" : initial,
                maxLength, Optional.empty()));
    }

    // Dialogs have no checkbox, so a boolean is an On/Off picker
    private static Input bool(String key, String label, boolean initial) {
        List<SingleOptionInput.Entry> entries = List.of(entry("true", "On", ChatFormatting.GREEN, initial),
                entry("false", "Off", ChatFormatting.RED, !initial));
        return singleOption(key, label, entries);
    }

    private static SingleOptionInput.Entry entry(String id, String display, ChatFormatting color, boolean initial) {
        return new SingleOptionInput.Entry(id, Optional.of(Component.literal(display).withStyle(color)), initial);
    }

    private static Input singleOption(String key, String label, List<SingleOptionInput.Entry> entries) {
        return new Input(key, new SingleOptionInput(INPUT_WIDTH, entries, Component.literal(label), true));
    }

    // ParsedTemplate has no public constructor, so go through its codec like the
    // dialog loader does
    private static Optional<Action> command(String template) {
        ParsedTemplate parsed = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template))
                .getOrThrow(msg -> new IllegalArgumentException("bad dialog command template: " + msg));
        return Optional.of(new CommandTemplate(parsed));
    }
}
