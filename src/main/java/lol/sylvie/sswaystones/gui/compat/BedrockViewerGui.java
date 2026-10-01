/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lol.sylvie.sswaystones.gui.AccessMode;
import lol.sylvie.sswaystones.gui.ViewerUtil;
import lol.sylvie.sswaystones.storage.PlayerData;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import lol.sylvie.sswaystones.util.NameGenerator;
import net.minecraft.server.MinecraftServer;
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

        builder.validResultHandler(response -> onServerThread(player, () -> {
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
        }));

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

        builder.validResultHandler(response -> onServerThread(player, () -> {
            int selectedIndex = response.clickedButtonId();
            if (selectedIndex < forgettable.size()) {
                WaystoneRecord selectedWaystone = forgettable.get(selectedIndex);
                data.forget(selectedWaystone.getHash());
            }

            openGui(player, waystone, sendForm);
        }));

        return builder.build();
    }

    public static CustomForm getSettingsForm(ServerPlayer player, WaystoneRecord waystone) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(String.format("%s - Settings", waystone.getWaystoneName()));

        WaystoneRecord.AccessSettings accessSettings = waystone.getAccessSettings();
        builder.input("Waystone Name", NameGenerator.generateName(), waystone.getWaystoneName());

        // One access dropdown, same modes as the Java dialog
        AccessMode.Permissions perms = ViewerUtil.permissions(player);
        AccessMode currentMode = AccessMode.fromSettings(accessSettings.isServerOwned(), accessSettings.isGlobal(),
                accessSettings.hasTeam());
        List<AccessMode> modes = AccessMode.availableModes(currentMode, perms);
        List<String> modeLabels = new ArrayList<>();
        for (AccessMode m : modes)
            modeLabels.add(bedrockModeLabel(m));
        int defaultIndex = Math.max(modes.indexOf(currentMode), 0);
        builder.dropdown("Access", modeLabels, defaultIndex);

        builder.toggle("Hide Name", accessSettings.isNameHidden());

        builder.validResultHandler(response -> onServerThread(player, () -> {
            String name = response.asInput(0);
            if (name == null)
                return;

            // The form may have been open a while; an admin could have taken the waystone over since
            if (!waystone.canPlayerEdit(player))
                return;

            // 0 = name, 1 = access, 2 = hide name
            int selected = response.asDropdown(1);
            if (selected >= 0 && selected < modes.size()) {
                AccessMode mode = modes.get(selected);
                if (mode.isAllowed(perms)) {
                    accessSettings.setGlobal(mode.global());
                    accessSettings.setServerOwned(mode.serverOwned());
                    accessSettings.setTeam(mode.team(player.getTeam() != null ? player.getTeam().getName() : ""));
                }
            }

            boolean hideName = response.asToggle(2);
            accessSettings.setNameHidden(hideName);

            waystone.setWaystoneName(name);
        }));

        return builder.build();
    }

    // Floodgate delivers form responses off the server thread. Waystone data, player data and the world are only
    // safe to touch from the server thread, so every response handler hops there first
    private static void onServerThread(ServerPlayer player, Runnable task) {
        MinecraftServer server = player.level().getServer();
        if (server.isSameThread())
            task.run();
        else
            server.execute(task);
    }

    private static String bedrockModeLabel(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> "Private";
            case TEAM -> "Team";
            case GLOBAL -> "Global";
            case SERVER -> "Server-owned";
        };
    }
}
