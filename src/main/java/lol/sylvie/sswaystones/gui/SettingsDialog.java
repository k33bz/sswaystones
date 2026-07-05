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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The native 26.x server-Dialog settings screen for the JAVA path (the "dialog" alternative to the
 * frozen sgui {@code AccessSettingsGui}, selected by the {@code settings_ui} config flag). It folds
 * name-editing and the permission-gated access toggles into ONE submit form, exactly mirroring the
 * Bedrock cumulus {@code CustomForm} in {@link lol.sylvie.sswaystones.gui.compat.BedrockViewerGui}:
 * a name text input plus Global / Team / Server-Owned toggles, plus the Hide Name toggle (credit
 * Hellscaped, upstream PR #51).
 *
 * <p>
 * Native dialogs cannot carry arbitrary Java callbacks the way sgui does; instead each input is
 * bound to a {@code $(key)} placeholder and the "Done" button submits them to the permission-0
 * {@code /waystonesettings apply ...} backend command (see {@code WaystonesCommand}). The backend
 * re-checks the SAME permissions server-side before applying each field, so a hand-crafted command
 * can't escalate access. Which toggles are OFFERED here still follows the player's permissions.
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

        // LAYOUT sizing. The vanilla dialog stacks each input + body element on its own row; at
        // default GUI scale the whole form was overflowing vertically, which clipped the Done button
        // behind Cancel. So: a single short body line (one row instead of a wrapped block), narrower
        // inputs, and Done + Cancel placed SIDE-BY-SIDE in a 2-column action row (below) instead of
        // stacking. Presentation only — no reachable state or behavior changes.
        final int inputWidth = 140;

        List<DialogBody> body = new ArrayList<>();
        body.add(new PlainMessage(
                Component.translatable("gui.sswaystones.dialog_settings_instruction").withStyle(ChatFormatting.GRAY),
                200));

        List<Input> inputs = new ArrayList<>();
        inputs.add(DialogInputs.text("name", componentString("gui.sswaystones.change_name"),
                waystone.getWaystoneName(), 32, inputWidth));

        // Only offer a toggle the player is permitted to change. Fields not offered are passed
        // through untouched by the backend (it re-checks perms too). The Hide Name toggle is always
        // offered to anyone who can edit the waystone (parity with the Bedrock form addition).
        //
        // These use DIALOG-SPECIFIC label keys (dialog_toggle_*) that spell out what each setting
        // does, since input widgets can't carry tooltips. The frozen sgui menu keeps its own short
        // toggle_* labels — deliberately not shared.
        if (globalAvailable)
            inputs.add(DialogInputs.bool("global", componentString("gui.sswaystones.dialog_toggle_global"),
                    access.isGlobal(), inputWidth));
        if (teamAvailable)
            inputs.add(DialogInputs.bool("team", componentString("gui.sswaystones.dialog_toggle_team"),
                    access.hasTeam(), inputWidth));
        if (serverAvailable)
            inputs.add(DialogInputs.bool("server", componentString("gui.sswaystones.dialog_toggle_server"),
                    access.isServerOwned(), inputWidth));
        inputs.add(DialogInputs.bool("hidename", componentString("gui.sswaystones.dialog_toggle_hide_name"),
                access.isNameHidden(), inputWidth));

        // The submit command carries every placeholder; inputs that weren't offered resolve to a
        // literal "-" sentinel the backend treats as "leave unchanged".
        String template = "waystonesettings apply " + waystone.getHash() + " name:$(name)"
                + " global:" + (globalAvailable ? "$(global)" : "-") + " team:" + (teamAvailable ? "$(team)" : "-")
                + " server:" + (serverAvailable ? "$(server)" : "-") + " hidename:$(hidename)";

        // Done + Cancel share ONE row (columns = 2). Both live in the actions list; there is no
        // separate exitAction so they don't stack/overlap. Done carries a demo hover tooltip (only
        // action buttons support tooltips in this API — inputs/options do not). Cancel has an empty
        // action, so with DialogAction.CLOSE it simply closes the dialog.
        final int buttonWidth = 100;
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(new ActionButton(
                new CommonButtonData(Component.translatable("gui.sswaystones.dialog_done"),
                        Optional.of(Component.translatable("gui.sswaystones.dialog_done_tooltip")), buttonWidth),
                DialogInputs.command(template)));
        buttons.add(new ActionButton(
                new CommonButtonData(Component.translatable("gui.sswaystones.dialog_cancel"), buttonWidth),
                Optional.empty()));

        CommonDialogData common = new CommonDialogData(
                Component.translatable("gui.sswaystones.access_settings"), Optional.empty(),
                true, // closable with escape
                false, // never pause the server
                DialogAction.CLOSE, // buttons close; the backend reopens the viewer
                body, inputs);

        Dialog dialog = new MultiActionDialog(common, buttons, Optional.empty(), 2);

        player.openDialog(Holder.direct(dialog));
    }

    // Dialog INPUT labels take a plain Component; using the translation key's fallback string keeps
    // the label human-readable server-side without needing a resolved client locale.
    private static String componentString(String translationKey) {
        return Component.translatable(translationKey).getString();
    }
}
