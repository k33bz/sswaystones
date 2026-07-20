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
import me.lucko.fabric.api.permissions.v0.Permissions;
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
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Native server-dialog settings screen for Java players — the "dialog" option
 * of the {@code settings_ui} config flag. Folds name editing, the access
 * selector, and the hide-name toggle into one submit form, mirroring the
 * Bedrock form in {@link lol.sylvie.sswaystones.gui.compat.BedrockViewerGui}.
 *
 * <p>
 * Dialogs cannot carry server-side callbacks, so each input is bound to a
 * {@code $(key)} placeholder and Done submits them through
 * {@code /waystonesettings apply}, which re-checks the player's permissions
 * before applying each field.
 */
public final class SettingsDialog {
    private static final int INPUT_WIDTH = 140;
    private static final int BUTTON_WIDTH = 100;

    private SettingsDialog() {
    }

    public static void open(ServerPlayer player, WaystoneRecord waystone) {
        WaystoneRecord.AccessSettings access = waystone.getAccessSettings();

        boolean globalAvailable = Permissions.check(player, "sswaystones.create.global", true);
        PlayerTeam team = player.getTeam();
        boolean teamAvailable = team != null && Permissions.check(player, "sswaystones.create.team", true);
        boolean serverAvailable = Permissions.check(player, "sswaystones.create.server", PermissionLevel.ADMINS);

        List<DialogBody> body = List.of(new PlainMessage(
                Component.translatable("gui.sswaystones.dialog_access_help").withStyle(ChatFormatting.GRAY), 200));

        // Options are filtered by permission, but the waystone's current mode is
        // always offered so re-saving never silently downgrades it.
        AccessMode currentMode = AccessMode.fromSettings(access.isServerOwned(), access.isGlobal(), access.hasTeam());
        List<SingleOptionInput.Entry> accessEntries = new ArrayList<>();
        for (AccessMode mode : AccessMode.availableModes(currentMode, teamAvailable, globalAvailable, serverAvailable))
            accessEntries.add(entry(mode.id(), modeLabel(mode), modeColor(mode), mode == currentMode));

        List<Input> inputs = new ArrayList<>();
        inputs.add(text("name", componentString("gui.sswaystones.change_name"), waystone.getWaystoneName(), 32));
        inputs.add(singleOption("access", componentString("gui.sswaystones.dialog_access_label"), accessEntries));
        inputs.add(bool("hidename", componentString("gui.sswaystones.toggle_hide_name"), access.isNameHidden()));

        // name: goes last — ApplyArgs parses it greedily to end-of-string, so a
        // name may contain spaces and colons (e.g. "Base: north").
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

    // Dialog input labels take a plain string; the translation key's fallback
    // keeps it readable server-side without a resolved client locale.
    private static String componentString(String translationKey) {
        return Component.translatable(translationKey).getString();
    }

    private static String modeLabel(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> componentString("gui.sswaystones.access_private");
            case TEAM -> componentString("gui.sswaystones.access_team");
            case GLOBAL -> componentString("gui.sswaystones.access_global");
            case SERVER -> componentString("gui.sswaystones.access_server");
        };
    }

    private static ChatFormatting modeColor(AccessMode mode) {
        return lol.sylvie.sswaystones.util.WaystoneColors.modeColor(mode);
    }

    private static Input text(String key, String label, String initial, int maxLength) {
        return new Input(key, new TextInput(INPUT_WIDTH, Component.literal(label), true, initial == null ? "" : initial,
                maxLength, Optional.empty()));
    }

    // The dialog API has no checkbox, so booleans are an On/Off single-option
    // picker whose selected id ("true"/"false") is substituted into the command.
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

    // ParsedTemplate has no public constructor, so decode through its string
    // codec — the same path the vanilla dialog loader uses.
    private static Optional<Action> command(String template) {
        ParsedTemplate parsed = ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(template))
                .getOrThrow(msg -> new IllegalArgumentException("bad dialog command template: " + msg));
        return Optional.of(new CommandTemplate(parsed));
    }
}
