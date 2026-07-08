/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

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
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.SingleOptionInput;
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
    private SettingsDialog() {
    }

    public static void open(ServerPlayer player, WaystoneRecord waystone) {
        WaystoneRecord.AccessSettings access = waystone.getAccessSettings();

        boolean globalAvailable = Permissions.check(player, "sswaystones.create.global", true);
        PlayerTeam team = player.getTeam();
        boolean teamAvailable = team != null && Permissions.check(player, "sswaystones.create.team", true);
        boolean serverAvailable = Permissions.check(player, "sswaystones.create.server", PermissionLevel.ADMINS);

        // The dialog stacks every element vertically and overflows at default GUI
        // scale, so keep it short: narrow inputs, one helper line, and Done/Cancel
        // sharing a row.
        final int inputWidth = 140;

        List<DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(
                Component.translatable("gui.sswaystones.dialog_access_help").withStyle(ChatFormatting.GRAY), 200));

        List<Input> inputs = new ArrayList<>();
        inputs.add(DialogInputs.text("name", componentString("gui.sswaystones.change_name"), waystone.getWaystoneName(),
                32, inputWidth));

        // One access selector instead of three toggles. Options are filtered by
        // permission, but the waystone's current mode is always offered so re-saving
        // never silently downgrades it.
        AccessMode currentMode = AccessMode.fromSettings(access.isServerOwned(), access.isGlobal(), access.hasTeam());
        List<AccessMode> modes = AccessMode.availableModes(currentMode, teamAvailable, globalAvailable,
                serverAvailable);
        List<SingleOptionInput.Entry> accessEntries = new ArrayList<>();
        for (AccessMode m : modes)
            accessEntries.add(DialogInputs.entry(m.id(), modeLabel(m), modeColor(m), m == currentMode));
        inputs.add(DialogInputs.singleOption("access", componentString("gui.sswaystones.dialog_access_label"),
                inputWidth, accessEntries));

        inputs.add(DialogInputs.bool("hidename", componentString("gui.sswaystones.toggle_hide_name"),
                access.isNameHidden(), inputWidth));

        // name: goes last — ApplyArgs parses it greedily to end-of-string, so a
        // name may contain spaces and colons (e.g. "Base: north").
        String template = "waystonesettings apply " + waystone.getHash()
                + " access:$(access) hidename:$(hidename) name:$(name)";

        // Done and Cancel live in one two-column action row; Cancel has no action,
        // so with DialogAction.CLOSE it simply closes the dialog.
        final int buttonWidth = 100;
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(new ActionButton(
                new CommonButtonData(Component.translatable("gui.sswaystones.dialog_done"),
                        Optional.of(Component.translatable("gui.sswaystones.dialog_done_tooltip")), buttonWidth),
                DialogInputs.command(template)));
        buttons.add(new ActionButton(
                new CommonButtonData(Component.translatable("gui.sswaystones.dialog_cancel"), buttonWidth),
                Optional.empty()));

        CommonDialogData common = new CommonDialogData(Component.translatable("gui.sswaystones.access_settings"),
                Optional.empty(), true, // closable with escape
                false, // never pause the server
                DialogAction.CLOSE, // buttons close; the backend reopens the viewer
                body, inputs);

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

    // Colored by openness: gray (private) through gold (server-owned).
    private static ChatFormatting modeColor(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> ChatFormatting.GRAY;
            case TEAM -> ChatFormatting.AQUA;
            case GLOBAL -> ChatFormatting.GREEN;
            case SERVER -> ChatFormatting.GOLD;
        };
    }
}
