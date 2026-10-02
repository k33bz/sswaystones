/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.block.WaystoneBlock;
import lol.sylvie.sswaystones.config.Configuration;
import lol.sylvie.sswaystones.gui.AccessIcons;
import lol.sylvie.sswaystones.gui.AccessMode;
import lol.sylvie.sswaystones.gui.ViewerUtil;
import lol.sylvie.sswaystones.util.HashUtil;
import lol.sylvie.sswaystones.util.SkinCache;
import lol.sylvie.sswaystones.util.WaystoneNames;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

public final class WaystoneRecord {
    private UUID owner;
    private String ownerName;
    private String waystoneName;
    private final BlockPos pos; // Must be final as the hash is calculated based on pos and world
    private final ResourceKey<Level> world;
    private final AccessSettings accessSettings;
    private Item icon;

    public static final Codec<WaystoneRecord> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(UUIDUtil.AUTHLIB_CODEC.fieldOf("waystone_owner").forGetter(WaystoneRecord::getOwnerUUID),
                    Codec.STRING.fieldOf("waystone_owner_name").forGetter(WaystoneRecord::getOwnerName),
                    Codec.STRING.fieldOf("waystone_name").forGetter(WaystoneRecord::getWaystoneName),
                    BlockPos.CODEC.fieldOf("position").forGetter(WaystoneRecord::getPos),
                    Level.RESOURCE_KEY_CODEC.fieldOf("world").forGetter(WaystoneRecord::getWorldKey),
                    AccessSettings.CODEC.optionalFieldOf("access_settings")
                            .forGetter((i) -> Optional.of(i.getAccessSettings())),
                    BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon", Items.PLAYER_HEAD)
                            .forGetter(WaystoneRecord::getIcon))
            .apply(instance, WaystoneRecord::new));

    // Optional fields share the same instance of a default value, so we have to use
    // this weird workaround
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private WaystoneRecord(UUID owner, String ownerName, String waystoneName, BlockPos pos, ResourceKey<Level> world,
            Optional<AccessSettings> accessSettings, Item icon) {
        this(owner, ownerName, waystoneName, pos, world,
                accessSettings.orElseGet(() -> new AccessSettings(false, false, "")), icon);
    }

    public WaystoneRecord(UUID owner, String ownerName, String waystoneName, BlockPos pos, ResourceKey<Level> world,
            AccessSettings accessSettings, Item icon) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.setWaystoneName(waystoneName); // Limits waystone name
        this.pos = pos;
        this.world = world;
        this.accessSettings = accessSettings;
        this.icon = icon == null ? Items.PLAYER_HEAD : icon;
    }

    // Landing spots beside the waystone, checked in this order
    private static final List<Vec3i> LANDING_CHECKS = List.of(new Vec3i(-1, -1, 0), new Vec3i(1, -1, 0),
            new Vec3i(0, -1, -1), new Vec3i(0, -1, 1), new Vec3i(-1, -1, -1), new Vec3i(1, -1, 1),
            new Vec3i(1, -1, -1), new Vec3i(-1, -1, 1));

    public void handleTeleport(ServerPlayer player) {
        Level world = player.level();
        MinecraftServer server = world.getServer();
        assert server != null;

        // Run on main thread to avoid a race condition for Geyser players
        if (!server.isSameThread()) {
            server.execute(() -> handleTeleport(player));
            return;
        }

        Configuration.Instance config = Waystones.configuration.getInstance();

        // The viewer may have been open for a while, so everything the viewer checked when it opened is checked
        // again at click time: combat, whether this waystone still exists, and whether the player may still use it
        if (Waystones.isInCombat(player)) {
            player.sendOverlayMessage(
                    Component.translatable("error.sswaystones.combat_cooldown").withStyle(ChatFormatting.RED));
            return;
        }

        WaystoneStorage storage = WaystoneStorage.getServerState(server);
        if (storage.getWaystone(this.getHash()) != this) {
            player.sendSystemMessage(
                    Component.translatable("error.sswaystones.invalid_waystone").withStyle(ChatFormatting.RED));
            return;
        }

        if (!this.getAccessSettings().canPlayerAccess(this, player)) {
            player.sendOverlayMessage(
                    Component.translatable("error.sswaystones.no_access").withStyle(ChatFormatting.RED));
            return;
        }

        // This may happen if someone has a waystone in a dimension from a mod that is
        // no longer present
        ServerLevel targetWorld = this.getWorld(server);
        if (targetWorld == null) {
            player.sendSystemMessage(
                    Component.translatable("error.sswaystones.no_dimension").withStyle(ChatFormatting.RED));
            return;
        }

        // Remove invalid waystones
        BlockPos waystonePos = this.getPos();
        if (!(targetWorld.getBlockState(waystonePos).getBlock() instanceof WaystoneBlock)
                && config.removeInvalidWaystones) {
            storage.destroyWaystone(this);
            player.sendSystemMessage(
                    Component.translatable("error.sswaystones.invalid_waystone").withStyle(ChatFormatting.RED));
            return;
        }

        // Teleporting never breaks or places blocks. Paranoid teleport used to break the block above the waystone
        // with drops and build a cobblestone floor, which ignored claims and let anyone empty a chest stacked on
        // someone else's waystone. Now, with paranoid teleport on, a waystone with nowhere safe to stand is refused
        BlockPos target = findLanding(targetWorld, waystonePos);
        if (target == null) {
            if (config.safeTeleport) {
                player.sendOverlayMessage(
                        Component.translatable("error.sswaystones.no_safe_spot").withStyle(ChatFormatting.RED));
                return;
            }
            target = waystonePos; // Paranoid teleport off: land on the waystone, as before
        }

        // Experience cost, charged last so a refused teleport costs nothing
        int requiredXp = getXpCost(player);
        if (requiredXp > 0) {
            if (player.experienceLevel < requiredXp) {
                player.sendOverlayMessage(
                        Component.translatable("error.sswaystones.not_enough_xp", requiredXp - player.experienceLevel)
                                .withStyle(ChatFormatting.RED));
                return;
            } else {
                player.giveExperienceLevels(Math.min(-requiredXp, 0)); // Stop negative values from adding xp
            }
        }

        // Teleport!
        Vec3 center = Vec3.atBottomCenterOf(target);
        player.teleportTo(targetWorld, center.x(), center.y(), center.z(), Set.of(), player.getYRot(), player.getXRot(),
                false);
        targetWorld.playSound(null, target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        targetWorld.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), center.x(),
                center.y() + 1f, center.z(), 16, 0.5d, 0.5d, 0.5d, 0.1d);
    }

    // First spot beside the waystone with solid ground and two clear blocks above it, or null if there is none
    @Nullable
    private static BlockPos findLanding(ServerLevel level, BlockPos waystonePos) {
        for (Vec3i check : LANDING_CHECKS) {
            BlockPos ground = waystonePos.offset(check);
            BlockPos feet = ground.above();
            BlockPos head = feet.above();
            if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty() && isClear(level, feet)
                    && isClear(level, head))
                return feet;
        }
        return null;
    }

    // Nothing to collide with, and not lava or fire
    private static boolean isClear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA)
                && !state.is(BlockTags.FIRE);
    }

    public boolean canPlayerEdit(ServerPlayer player) {
        return AccessMode.canEdit(this.getAccessSettings().isServerOwned(),
                this.getOwnerUUID().equals(player.getUUID()),
                Permissions.check(player, "sswaystones.manager", PermissionLevel.ADMINS));
    }

    public int getXpCost(ServerPlayer player) {
        Configuration.Instance config = Waystones.configuration.getInstance();
        if (player.isCreative())
            return 0;
        return player.level().dimension().equals(this.getWorldKey()) ? config.xpCost : config.crossDimensionXpCost;
    }

    public ItemStack getIconOrHead(@Nullable MinecraftServer server) {
        // Public waystones wear a globe instead of the owner's icon
        if (Waystones.configuration.getInstance().accessModeIcons) {
            AccessMode mode = AccessMode.fromSettings(accessSettings.isServerOwned(), accessSettings.isGlobal(),
                    !accessSettings.getTeam().isEmpty());
            if (AccessMode.usesMarkerIcon(mode, icon != null && icon != Items.PLAYER_HEAD)) {
                ItemStack marker = AccessIcons.iconFor(mode);
                if (marker != null)
                    return marker;
            }
        }

        if (icon != null && icon != Items.PLAYER_HEAD)
            return icon.getDefaultInstance();

        // The owner's skin comes from Mojang. SkinCache looks it up off the server thread, so this never blocks
        GameProfile profile = server != null
                ? SkinCache.profileFor(server, this.getOwnerUUID(), this.getOwnerName())
                : new GameProfile(this.getOwnerUUID(), this.getOwnerName());

        ItemStack head = Items.PLAYER_HEAD.getDefaultInstance();
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }

    // Getters and setters
    public UUID getOwnerUUID() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().name();
    }

    public String getWaystoneName() {
        return waystoneName;
    }

    // Every name passes through here, from the anvil, dialog, Bedrock form, command and old saves alike
    public void setWaystoneName(String waystoneName) {
        this.waystoneName = WaystoneNames.sanitize(waystoneName);
    }

    public BlockPos getPos() {
        return pos;
    }

    public ResourceKey<Level> getWorldKey() {
        return world;
    }

    public AccessSettings getAccessSettings() {
        return accessSettings;
    }

    public Item getIcon() {
        return icon;
    }

    public void setIcon(Item icon) {
        this.icon = icon;
    }

    public static class AccessSettings {
        private boolean global; // Blanket flag, allows all players to access
        private boolean server; // Hides the actual owner and makes it unbreakable
        private String team; // Scoreboard team
        private boolean hideName; // Hides the floating name hologram

        // Optional so older saves still load
        public static final Codec<AccessSettings> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(Codec.BOOL.fieldOf("global").forGetter(AccessSettings::isGlobal),
                        Codec.BOOL.fieldOf("server").forGetter(AccessSettings::isServerOwned),
                        Codec.STRING.fieldOf("team").forGetter(AccessSettings::getTeam),
                        Codec.BOOL.optionalFieldOf("hide_name", false).forGetter(AccessSettings::isNameHidden))
                .apply(instance, AccessSettings::new));

        public AccessSettings(boolean global, boolean server, String team) {
            this(global, server, team, false);
        }

        public AccessSettings(boolean global, boolean server, String team, boolean hideName) {
            this.global = global;
            this.server = server;
            this.team = team;
            this.hideName = hideName;
        }

        public boolean isEffectivelyGlobal() {
            return this.isGlobal() || this.isServerOwned();
        }

        public boolean canPlayerAccess(WaystoneRecord parent, ServerPlayer player) {
            if (ViewerUtil.mayAccessAll.contains(player.getUUID())
                    && Permissions.check(player, "sswaystones.showall", PermissionLevel.ADMINS))
                return true;

            PlayerData data = WaystoneStorage.getPlayerState(player);
            if (data.discoveredWaystones.contains(parent.getHash()))
                return true;

            if (this.isEffectivelyGlobal())
                return true;

            PlayerTeam team = player.getTeam();
            if (team != null && team.getName().equals(this.team))
                return true;

            return false;
        }

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public boolean isServerOwned() {
            return server;
        }

        public void setServerOwned(boolean server) {
            this.server = server;
        }

        public String getTeam() {
            return team;
        }

        public void setTeam(String team) {
            this.team = team;
        }

        public boolean hasTeam() {
            return !this.getTeam().isEmpty();
        }

        public boolean isNameHidden() {
            return hideName;
        }

        public void setNameHidden(boolean hideName) {
            this.hideName = hideName;
        }
    }

    public String asString() {
        return HashUtil.waystoneIdentifier(pos, world);
    }

    public String getHash() {
        return HashUtil.getHash(this);
    }

    public Component getWaystoneText() {
        return Component.literal(this.getWaystoneName());
    }

    public ServerLevel getWorld(MinecraftServer server) {
        return server.getLevel(this.getWorldKey());
    }
}
