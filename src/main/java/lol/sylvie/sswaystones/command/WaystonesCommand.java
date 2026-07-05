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
        dispatcher.register(literal("waystonesettings")
                .then(literal("apply").then(
                        argument("args", StringArgumentType.greedyString()).executes(WaystonesCommand::applySettings)))
                // Test-support hooks (permission 0) so the headless bot suite can drive flows that
                // ride on client interactions ViaProxy doesn't bridge (right-click a waystone to
                // open the viewer / sneak-settings). testcreate makes a waystone at the caller's feet
                // and prints its hash; testopen opens the Java viewer for a given hash.
                .then(literal("testcreate").executes(WaystonesCommand::testCreate))
                .then(literal("testopen").then(argument("hash", StringArgumentType.word())
                        .executes(WaystonesCommand::testOpen)))
                .then(literal("get").then(argument("hash", StringArgumentType.word())
                        .executes(WaystonesCommand::testGet))));
    }

    // Echoes the stored settings for a waystone in a stable, parseable line so the bot suite can
    // use RCON as the assertion oracle for the settings round-trip (the SavedData store and the
    // polymer virtual-entity name hologram aren't directly queryable via /data get entity).
    //   waystone_settings <hash> name=<name> global=<t/f> server=<t/f> team=<team-or-> hidename=<t/f>
    private static int testGet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
        WaystoneRecord record = storage.getWaystone(StringArgumentType.getString(context, "hash"));
        if (record == null) {
            throw new CommandSyntaxException(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                    Component.translatable("command.sswaystones.waystone_not_found"));
        }
        WaystoneRecord.AccessSettings a = record.getAccessSettings();
        String line = String.format("waystone_settings %s name=%s global=%s server=%s team=%s hidename=%s",
                record.getHash(), record.getWaystoneName(), a.isGlobal(), a.isServerOwned(),
                a.hasTeam() ? a.getTeam() : "-", a.isNameHidden());
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    // Creates a waystone at the caller's block position (as if placed by them) and echoes the hash,
    // so a bot can obtain a real, storage-tracked waystone deterministically over RCON/command.
    private static int testCreate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
        WaystoneRecord record = storage.createWaystone(player.blockPosition(), player.level(), player);
        if (record == null)
            return 0; // createWaystone already messaged the player (perm/limit)
        String hash = record.getHash();
        context.getSource().sendSuccess(() -> Component.literal("waystone_created " + hash), false);
        return 1;
    }

    // Opens the Java viewer for the given waystone hash (routes through ViewerUtil exactly like a
    // right-click would), so the viewer path is reachable without a use_entity packet.
    private static int testOpen(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
        WaystoneRecord record = storage.getWaystone(StringArgumentType.getString(context, "hash"));
        if (record == null) {
            throw new CommandSyntaxException(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument(),
                    Component.translatable("command.sswaystones.waystone_not_found"));
        }
        ViewerUtil.openGui(player, record);
        return 1;
    }

    // Parses "key:value" tokens out of the greedy args tail. Values may contain spaces only for the
    // name field, which is always LAST-parsed here by consuming to the next known key; to keep this
    // simple and robust we require name to be passed with no embedded " global:"/" team:" etc.
    // sequences (names are capped at 32 chars and sanitized by setWaystoneName regardless).
    private static int applySettings(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String raw = StringArgumentType.getString(context, "args");

        // hash is the first bare token (no "key:") — pull it as the leading word.
        String[] head = raw.trim().split("\\s+", 2);
        String hash = head.length > 0 ? head[0] : "";
        String rest = head.length > 1 ? head[1] : "";

        WaystoneStorage storage = WaystoneStorage.getServerState(context.getSource().getServer());
        WaystoneRecord waystone = storage.getWaystone(hash);
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

        String name = extractToken(rest, "name");
        if (name != null && !name.equals("-"))
            waystone.setWaystoneName(name);

        String global = extractToken(rest, "global");
        if (isSet(global) && Permissions.check(player, "sswaystones.create.global", true))
            access.setGlobal(parseBool(global));

        String team = extractToken(rest, "team");
        if (isSet(team) && player.getTeam() != null && Permissions.check(player, "sswaystones.create.team", true))
            access.setTeam(parseBool(team) ? player.getTeam().getName() : "");

        String server = extractToken(rest, "server");
        if (isSet(server) && Permissions.check(player, "sswaystones.create.server", PermissionLevel.ADMINS))
            access.setServerOwned(parseBool(server));

        // Single "access mode" form used by the native dialog's Access selector. Maps one mutually-
        // exclusive mode back to the same three fields, re-checking the SAME permissions server-side
        // (PRIVATE always allowed) so a hand-crafted access:server can't escalate. The per-field
        // global:/team:/server: params above still work for any other caller.
        String accessMode = extractToken(rest, "access");
        if (isSet(accessMode)) {
            AccessMode mode = AccessMode.fromId(accessMode);
            boolean canTeam = player.getTeam() != null && Permissions.check(player, "sswaystones.create.team", true);
            boolean canGlobal = Permissions.check(player, "sswaystones.create.global", true);
            boolean canServer = Permissions.check(player, "sswaystones.create.server", PermissionLevel.ADMINS);
            if (mode.isAllowed(canTeam, canGlobal, canServer)) {
                access.setGlobal(mode.global());
                access.setServerOwned(mode.serverOwned());
                access.setTeam(mode.team(player.getTeam() != null ? player.getTeam().getName() : ""));
            }
        }

        String hidename = extractToken(rest, "hidename");
        if (isSet(hidename))
            access.setNameHidden(parseBool(hidename));

        // Persist (SavedData is marked dirty on every getServerState) and reopen the viewer.
        ViewerUtil.openGui(player, waystone);
        return 1;
    }

    // A field is "set" (should be applied) when present and not the "-" leave-unchanged sentinel.
    private static boolean isSet(String v) {
        return v != null && !v.equals("-");
    }

    private static boolean parseBool(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v);
    }

    // Pull the value of "key:" up to the next " <word>:" boundary (or end of string).
    private static String extractToken(String s, String key) {
        if (s == null)
            return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:^|\\s)" + java.util.regex.Pattern.quote(key) + ":(.*?)(?=\\s+\\w+:|$)")
                .matcher(s);
        return m.find() ? m.group(1).trim() : null;
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
