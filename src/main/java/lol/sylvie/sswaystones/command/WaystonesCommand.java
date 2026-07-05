/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Field;
import java.util.*;
import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.block.ModBlocks;
import lol.sylvie.sswaystones.config.Configuration;
import lol.sylvie.sswaystones.config.Description;
import lol.sylvie.sswaystones.gui.AccessMode;
import lol.sylvie.sswaystones.gui.ViewerUtil;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import lol.sylvie.sswaystones.util.NameGenerator;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

public class WaystonesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("sswaystones")
                .requires(source -> Permissions.check(source, "sswaystones.command", PermissionLevel.ADMINS))
                .executes(context -> {
                    String version = FabricLoader.getInstance().getModContainer(Waystones.MOD_ID).orElseThrow()
                            .getMetadata().getVersion().getFriendlyString();
                    context.getSource().sendSuccess(
                            () -> Component.literal("sswaystones " + version + ", made with <3 by sylvie"), false);
                    return 1;
                }).then(literal("list").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("command.sswaystones.list_header"),
                            false);

                    WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
                    for (Map.Entry<String, WaystoneRecord> waystone : storage.waystones.entrySet()) {
                        WaystoneRecord record = waystone.getValue();
                        context.getSource().sendSuccess(
                                () -> Component.literal(
                                        String.format("(%s) [%s >" + " %s] %s", waystone.getKey().substring(0, 7),
                                                record.getOwnerName(), record.getWaystoneName(), record.asString())),
                                false);
                    }

                    return 1;
                })).then(literal("remove").then(argument("hash", StringArgumentType.word()).executes(context -> {
                    WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
                    String search = StringArgumentType.getString(context, "hash").toLowerCase(Locale.ROOT);
                    Optional<Map.Entry<String, WaystoneRecord>> entry = storage.waystones.entrySet().stream()
                            .filter(w -> w.getKey().toLowerCase(Locale.ROOT).startsWith(search)).findFirst();
                    if (entry.isEmpty()) {
                        throw new CommandSyntaxException(
                                CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                                Component.translatable("command.sswaystones.waystone_not_found"));
                    }

                    MinecraftServer server = context.getSource().getServer();
                    WaystoneRecord record = entry.get().getValue();
                    storage.destroyWaystone(record);

                    // Remove it in the world
                    ServerLevel world = record.getWorld(server);
                    if (world.getBlockState(record.getPos()).is(ModBlocks.WAYSTONE)) {
                        world.destroyBlock(record.getPos(), true);
                    }

                    context.getSource().sendSuccess(
                            () -> Component.translatable("command.sswaystones.waystone_removed_successfully"), true);

                    return 1;
                })))
                .then(literal("showall")
                        .requires(source -> Permissions.check(source, "sswaystones.showall", PermissionLevel.ADMINS))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            UUID uuid = player.getUUID();
                            if (ViewerUtil.mayAccessAll.contains(uuid)) {
                                ViewerUtil.mayAccessAll.remove(uuid);
                                context.getSource().sendSuccess(() -> Component
                                        .translatable("command.sswaystones.showall_off").withStyle(ChatFormatting.RED),
                                        true);
                            } else {
                                ViewerUtil.mayAccessAll.add(uuid);
                                context.getSource().sendSuccess(() -> Component
                                        .translatable("command.sswaystones.showall_on").withStyle(ChatFormatting.GREEN),
                                        true);
                            }

                            return 1;
                        }))
                .then(literal("config").then(literal("help").executes(context -> {
                    context.getSource()
                            .sendSuccess(() -> Component.translatable("command.sswaystones.config_help_header"), false);
                    for (Map.Entry<String, Component> option : getConfigOptions().entrySet()) {
                        context.getSource()
                                .sendSuccess(() -> Component.translatable("command.sswaystones.config_format",
                                        formatKey(option.getKey()), option.getValue()), false);
                    }
                    return 1;
                })).then(literal("reload").executes(context -> {
                    Waystones.configuration.load();
                    context.getSource().sendSuccess(
                            () -> Component.translatable("command.sswaystones.config_reload_success"), true);
                    return 1;
                })).then(literal("save").executes(context -> {
                    Waystones.configuration.save();
                    context.getSource().sendSuccess(
                            () -> Component.translatable("command.sswaystones.config_save_success"), false);
                    return 1;
                })).then(literal("set").then(argument("key", StringArgumentType.word())
                        .then(argument("value", StringArgumentType.greedyString()).executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            Field field = getConfigByName(key);
                            if (field == null)
                                throw new CommandSyntaxException(
                                        CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                                        Component.translatable("command.sswaystones.config_not_found"));
                            Configuration.Instance instance = Waystones.configuration.getInstance();
                            field.setAccessible(true);

                            Class<?> type = field.getType();
                            String value = StringArgumentType.getString(context, "value");
                            Object newValue;
                            try {
                                if (type == int.class) {
                                    field.set(instance, Integer.parseInt(value));
                                } else if (type == float.class) {
                                    field.set(instance, Float.parseFloat(value));
                                } else if (type == double.class) {
                                    field.set(instance, Double.parseDouble(value));
                                } else if (type == boolean.class) {
                                    field.set(instance, Boolean.parseBoolean(value));
                                } else if (type == String.class) {
                                    field.set(instance, value);
                                } else if (type == List.class) {
                                    field.set(instance,
                                            List.of(value.replaceAll("^\\[\\s*|\\s*]$|\"", "").split(",\\s")));
                                }
                                newValue = field.get(instance);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            } catch (NumberFormatException e) {
                                throw new CommandSyntaxException(
                                        CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt(),
                                        Component.translatable("command.sswaystones.config_set_invalid_type"));
                            }

                            NameGenerator.reloadFiles();

                            context.getSource()
                                    .sendSuccess(() -> Component.translatable("command.sswaystones.config_set_success",
                                            formatKey(key), formatValue(newValue)), true);
                            return 1;
                        }))))
                        .then(literal("get").then(argument("key", StringArgumentType.string()).executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            Field field = getConfigByName(key);
                            if (field == null)
                                throw new CommandSyntaxException(
                                        CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                                        Component.translatable("command.sswaystones.config_not_found"));
                            Configuration.Instance instance = Waystones.configuration.getInstance();

                            context.getSource().sendSuccess(() -> {
                                try {
                                    return Component.translatable("command.sswaystones.config_format", formatKey(key),
                                            formatValue(field.get(instance)));
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            }, false);

                            return 1;
                        })))));

        // Permission-0 backend for the native settings Dialog (see SettingsDialog). Any player who
        // can edit the target waystone may submit; the handler re-checks canPlayerEdit and the SAME
        // per-field permissions the Dialog used to decide which toggles to offer, so a hand-crafted
        // command cannot escalate access. Also serves as the headless bot-test hook.
        //
        //   waystonesettings apply <hash> name:<...> global:<t/f/-> team:<t/f/-> server:<t/f/-> hidename:<t/f/->
        //
        // A field value of "-" means "leave unchanged" (the Dialog emits it for toggles the player
        // wasn't offered). The whole tail is a single greedy string because dialog $(key)
        // substitution produces one argument.
        dispatcher.register(literal("waystonesettings").then(literal("apply")
                .then(argument("args", StringArgumentType.greedyString()).executes(WaystonesCommand::applySettings))));
    }

    // Applies the /waystonesettings apply tail. All string parsing lives in the dependency-free,
    // unit-tested ApplyArgs (handles the "-" leave-unchanged sentinel, a name containing a colon, and
    // the access: vs legacy-field precedence). AccessMode is the intended single source of truth for
    // the access mapping — see the cross-reference comments at the access-UI sites.
    private static int applySettings(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApplyArgs args = ApplyArgs.parse(StringArgumentType.getString(context, "args"));

        WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
        WaystoneRecord waystone = storage.getWaystone(args.hash());
        if (waystone == null) {
            throw new CommandSyntaxException(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                    Component.translatable("command.sswaystones.waystone_not_found"));
        }
        if (!waystone.canPlayerEdit(player)) {
            player.sendSystemMessage(
                    Component.translatable("error.sswaystones.no_modification_permission").withStyle(ChatFormatting.RED));
            return 0;
        }

        WaystoneRecord.AccessSettings access = waystone.getAccessSettings();

        // Snapshot BEFORE mutating so we can (a) detect a no-op Done and (b) log the prior state if we
        // are about to change access (which includes collapsing a legacy redundant combo). This makes
        // the normalization recoverable from the server log.
        boolean beforeGlobal = access.isGlobal();
        boolean beforeServer = access.isServerOwned();
        String beforeTeam = access.getTeam();
        boolean beforeHidden = access.isNameHidden();
        String beforeName = waystone.getWaystoneName();

        // Compute the intended NEW field values without mutating yet.
        String newName = args.newName().orElse(beforeName);

        boolean newGlobal = beforeGlobal;
        boolean newServer = beforeServer;
        String newTeam = beforeTeam;

        boolean canGlobal = Permissions.check(player, "sswaystones.create.global", true);
        boolean canTeam = player.getTeam() != null && Permissions.check(player, "sswaystones.create.team", true);
        boolean canServer = Permissions.check(player, "sswaystones.create.server", PermissionLevel.ADMINS);

        if (args.accessMode().isPresent()) {
            // Single "access mode" form (native dialog + Bedrock dropdown). Maps one mutually-exclusive
            // mode back to the three fields, re-checking the SAME permissions (PRIVATE always allowed)
            // so a hand-crafted access:server can't escalate. AccessMode is the single source of truth.
            AccessMode mode = args.accessMode().get();
            if (mode.isAllowed(canTeam, canGlobal, canServer)) {
                newGlobal = mode.global();
                newServer = mode.serverOwned();
                newTeam = mode.team(player.getTeam() != null ? player.getTeam().getName() : "");
            }
        } else {
            // Legacy per-field form (used only when access: is absent). Each field is independently
            // permission-gated.
            if (args.global().isPresent() && canGlobal)
                newGlobal = args.global().get();
            if (args.team().isPresent() && canTeam)
                newTeam = args.team().get() ? player.getTeam().getName() : "";
            if (args.server().isPresent() && canServer)
                newServer = args.server().get();
        }

        boolean newHidden = args.hideName().orElse(beforeHidden);

        // Skip the write entirely on a no-op Done (nothing actually changed) — no gratuitous mutation.
        boolean unchanged = newGlobal == beforeGlobal && newServer == beforeServer && newTeam.equals(beforeTeam)
                && newHidden == beforeHidden && newName.equals(beforeName);
        if (unchanged) {
            ViewerUtil.openGui(player, waystone);
            return 1;
        }

        // If we're about to change the ACCESS fields (incl. collapsing a legacy global+team combo to
        // one mode), log the prior AccessSettings at INFO so the previous state is recoverable.
        boolean accessChanging = newGlobal != beforeGlobal || newServer != beforeServer
                || !newTeam.equals(beforeTeam);
        if (accessChanging) {
            Waystones.LOGGER.info(
                    "Waystone {} ({}) access changing by {}: was [global={}, server={}, team='{}', hideName={}] -> "
                            + "[global={}, server={}, team='{}', hideName={}]",
                    waystone.getHash(), beforeName, player.getGameProfile().name(), beforeGlobal, beforeServer,
                    beforeTeam, beforeHidden, newGlobal, newServer, newTeam, newHidden);
        }

        // Commit.
        if (!newName.equals(beforeName))
            waystone.setWaystoneName(newName);
        access.setGlobal(newGlobal);
        access.setServerOwned(newServer);
        access.setTeam(newTeam);
        access.setNameHidden(newHidden);

        // Persist (SavedData is marked dirty on every getServerState) and reopen the viewer.
        ViewerUtil.openGui(player, waystone);
        return 1;
    }

    // Returns Map of String -> Description
    private static Map<String, Component> getConfigOptions() {
        // I'm not proud of this. I'm so, so sorry.
        Field[] fields = Configuration.Instance.class.getDeclaredFields();
        Map<String, Component> descriptionMap = new HashMap<>();
        for (Field field : fields) {
            SerializedName name = field.getAnnotation(SerializedName.class);
            Description description = field.getAnnotation(Description.class);
            descriptionMap.put(name.value(), Component.translatable(description.translation()));
        }
        return descriptionMap;
    }

    private static Field getConfigByName(String key) {
        Field[] fields = Configuration.Instance.class.getDeclaredFields();
        for (Field field : fields) {
            SerializedName name = field.getAnnotation(SerializedName.class);
            if (name.value().equals(key))
                return field;
        }
        return null;
    }

    private static Component formatKey(String key) {
        return Component.literal(key.toLowerCase()).withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE);
    }

    private static Component formatValue(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
    }
}
