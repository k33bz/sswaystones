/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lol.sylvie.sswaystones.gui.AccessMode;
import lol.sylvie.sswaystones.storage.PlayerData;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import lol.sylvie.sswaystones.util.NameGenerator;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.cumulus.component.ButtonComponent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.jetbrains.annotations.Nullable;

public class BedrockViewerGui {
    private static final String AVATAR_API = "https://api.tydiumcraft.net/v1/players/skin?uuid=%s&type=avatar";

    public static void openGui(ServerPlayer player, @Nullable WaystoneRecord waystone, Consumer<Form> sendForm) {
        SimpleForm form = BedrockViewerGui.getViewerForm(player, waystone, sendForm);
        sendForm.accept(form);
    }

    private static void addRecordButton(SimpleForm.Builder builder, WaystoneRecord record) {
        boolean server = record.getAccessSettings().isServerOwned();
        FormImage.Type type = server ? FormImage.Type.PATH : FormImage.Type.URL;
        String image = server
                ? "textures/ui/filledStar.png"
                : AVATAR_API.replace("%s", record.getOwnerUUID().toString());

        ButtonComponent component = ButtonComponent.of(record.getWaystoneName(), type, image);
        builder.button(component);
    }

    public static SimpleForm getViewerForm(ServerPlayer player, @Nullable WaystoneRecord waystone,
            Consumer<Form> sendForm) {
        String title = "Waystones";
        if (waystone != null) {
            title = String.format("%s [%s]", waystone.getWaystoneName(), waystone.getOwnerName());
        }

        SimpleForm.Builder builder = SimpleForm.builder().title(title);

        WaystoneStorage storage = WaystoneStorage.getServerState(player.level().getServer());
        List<WaystoneRecord> accessible = storage.getAccessibleWaystones(player, waystone);

        for (WaystoneRecord record : accessible) {
            addRecordButton(builder, record);
        }

        boolean showSettingsButton = waystone != null && waystone.canPlayerEdit(player);
        if (showSettingsButton)
            builder.button("Settings", FormImage.Type.PATH, "textures/gui/newgui/anvil-hammer.png");

        builder.button("Forget Waystones", FormImage.Type.PATH, "textures/ui/icon_trash.png");

        builder.validResultHandler(response -> {
            int selectedIndex = response.clickedButtonId();
            if (selectedIndex < accessible.size()) {
                WaystoneRecord selectedWaystone = accessible.get(selectedIndex);
                selectedWaystone.handleTeleport(player);
                return;
            }

            if (selectedIndex == accessible.size() && showSettingsButton) {
                CustomForm form = getSettingsForm(player, waystone);
                sendForm.accept(form);
            }

            if (selectedIndex == accessible.size() + (showSettingsButton ? 1 : 0)) {
                SimpleForm form = getDeleteForm(player, waystone, sendForm);
                sendForm.accept(form);
            }
        });

        return builder.build();
    }

    public static SimpleForm getDeleteForm(ServerPlayer player, @Nullable WaystoneRecord waystone,
            Consumer<Form> sendForm) {
        SimpleForm.Builder builder = SimpleForm.builder().title("Forget Waystone");
        WaystoneStorage storage = WaystoneStorage.getServerState(player.level().getServer());
        PlayerData data = WaystoneStorage.getPlayerState(player);
        List<WaystoneRecord> forgettable = storage.getAccessibleWaystones(player, waystone).stream()
                .filter(record -> record != waystone && !record.getAccessSettings().isEffectivelyGlobal()
                        && data.discoveredWaystones.contains(record.getHash()))
                .toList();

        for (WaystoneRecord record : forgettable) {
            addRecordButton(builder, record);
        }

        builder.button("Back", FormImage.Type.PATH, "textures/ui/cancel.png");

        builder.validResultHandler(response -> {
            int selectedIndex = response.clickedButtonId();
            if (selectedIndex < forgettable.size()) {
                WaystoneRecord selectedWaystone = forgettable.get(selectedIndex);
                data.discoveredWaystones.remove(selectedWaystone.getHash());
            }

            openGui(player, waystone, sendForm);
        });

        return builder.build();
    }

    public static CustomForm getSettingsForm(ServerPlayer player, WaystoneRecord waystone) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(String.format("%s - Settings", waystone.getWaystoneName()));

        WaystoneRecord.AccessSettings accessSettings = waystone.getAccessSettings();
        builder.input("Waystone Name", NameGenerator.generateName(), waystone.getWaystoneName());

        // ACCESS-UI SOURCE OF TRUTH: like the Java dialog (SettingsDialog), the Bedrock form presents
        // access as ONE dropdown routed through AccessMode — the single source of truth for the
        // options, the current-mode mapping, and the mode->fields apply. (The sgui AccessSettingsGui
        // keeps its own independent 3-toggle logic — a documented known divergence.)
        boolean globalAvailable = Permissions.check(player, "sswaystones.create.global", true);
        boolean teamAvailable = player.getTeam() != null && Permissions.check(player, "sswaystones.create.team", true);
        boolean serverAvailable = Permissions.check(player, "sswaystones.create.server", 4);

        AccessMode currentMode = AccessMode.fromSettings(accessSettings.isServerOwned(), accessSettings.isGlobal(),
                accessSettings.hasTeam());
        List<AccessMode> modes = AccessMode.availableModes(currentMode, teamAvailable, globalAvailable, serverAvailable);
        List<String> modeLabels = new ArrayList<>();
        for (AccessMode m : modes)
            modeLabels.add(bedrockModeLabel(m));
        int defaultIndex = Math.max(modes.indexOf(currentMode), 0);
        builder.dropdown("Access", modeLabels, defaultIndex);

        // Hide Name toggle (credit Hellscaped, upstream PR #51): offered to anyone who can edit the
        // waystone — Bedrock players can read the floating name through walls, so hiding it matters
        // most for them. Always present, LAST component.
        builder.toggle("Hide Name", accessSettings.isNameHidden());

        builder.validResultHandler(response -> {
            String name = response.asInput(0);
            if (name == null)
                return;

            // component order: 0 = name input, 1 = access dropdown, 2 = hide-name toggle.
            int selected = response.asDropdown(1);
            if (selected >= 0 && selected < modes.size()) {
                AccessMode mode = modes.get(selected);
                // Re-check the SAME permissions server-side (PRIVATE always allowed) — the current mode
                // is always offered but a player still can't APPLY a mode they lack permission for.
                boolean canServer = Permissions.check(player, "sswaystones.create.server", 4);
                if (mode.isAllowed(teamAvailable, globalAvailable, canServer)) {
                    accessSettings.setGlobal(mode.global());
                    accessSettings.setServerOwned(mode.serverOwned());
                    accessSettings.setTeam(mode.team(player.getTeam() != null ? player.getTeam().getName() : ""));
                }
            }

            boolean hideName = response.asToggle(2);
            accessSettings.setNameHidden(hideName);

            waystone.setWaystoneName(name);
        });

        return builder.build();
    }

    // Human-readable dropdown label for an access mode (Bedrock side; the Java dialog uses lang keys).
    private static String bedrockModeLabel(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> "Private";
            case TEAM -> "Team";
            case GLOBAL -> "Global";
            case SERVER -> "Server-owned";
        };
    }
}
