/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.announce;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.storage.PlayerData;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import lol.sylvie.sswaystones.storage.WaystoneStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Ambient locale announcements: when a player walks near a public landmark
 * waystone, its name is announced — a big title the FIRST time that player ever
 * reaches it ("Eventide" — a waypoint discovered), and a quiet actionbar line
 * on every later approach. Only global / server-owned waystones speak up, so
 * personal home waystones never turn a built-up base into a wall of banners.
 *
 * <p>
 * Deliberately a stop-over cue, not a safe-zone marker. The first-time set is
 * persisted per player (in {@link PlayerData#seenWaystones}); the "currently
 * standing near" latch is transient (in-memory), forgotten on disconnect, so
 * the announcement fires once per approach and re-arms when the player leaves.
 */
public final class WaystoneAnnouncer {
    private WaystoneAnnouncer() {
    }

    /**
     * Per-player latch: hash of the landmark they're currently within range of
     * (null/absent = none).
     */
    private static final Map<UUID, String> NEAR = new HashMap<>();

    /**
     * Scan cadence — a landmark scan every second is ample and keeps the per-tick
     * cost near zero.
     */
    private static final int SCAN_EVERY = 20;
    private static int counter = 0;

    /** Registered on END_SERVER_TICK. */
    public static void tick(MinecraftServer server) {
        Waystones.configuration.getInstance();
        if (!Waystones.configuration.getInstance().announceLandmarkOnApproach) {
            return;
        }
        if (++counter < SCAN_EVERY) {
            return;
        }
        counter = 0;

        int radius = Math.max(1, Waystones.configuration.getInstance().announceRadius);
        long r2 = (long) radius * radius;
        WaystoneStorage storage = WaystoneStorage.getServerState(server);
        var all = storage.getWaystones().values();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dim = player.level().dimension();
            WaystoneRecord nearest = null;
            double best = Double.MAX_VALUE;
            for (WaystoneRecord w : all) {
                WaystoneRecord.AccessSettings a = w.getAccessSettings();
                if (!(a.isGlobal() || a.isServerOwned())) {
                    continue; // landmarks only
                }
                if (!dim.equals(w.getWorldKey())) {
                    continue; // same dimension only (positions are meaningless across dimensions)
                }
                // Distance to the block's centre, computed by hand so this is mapping-agnostic
                // (BlockPos.getCenter() is absent in this hybrid mapping).
                double dx = (w.getPos().getX() + 0.5) - player.getX();
                double dy = (w.getPos().getY() + 0.5) - player.getY();
                double dz = (w.getPos().getZ() + 0.5) - player.getZ();
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= r2 && d2 < best) {
                    best = d2;
                    nearest = w;
                }
            }

            UUID id = player.getUUID();
            if (nearest == null) {
                NEAR.remove(id); // left every landmark's range — re-arm the next approach
                continue;
            }
            String hash = nearest.getHash();
            if (hash.equals(NEAR.get(id))) {
                continue; // still standing at the same landmark — don't repeat
            }
            NEAR.put(id, hash);
            announce(player, nearest, storage);
        }
    }

    private static void announce(ServerPlayer player, WaystoneRecord w, WaystoneStorage storage) {
        String name = w.getWaystoneName();
        if (name == null || name.isBlank()) {
            name = "a waystone";
        }
        PlayerData data = WaystoneStorage.getPlayerState(player);
        // "Discovered" also covers a waystone already unlocked for fast-travel, so
        // unlocking then walking
        // past it doesn't fire a spurious first-time title.
        boolean firstTime = !data.getSeenWaystones().contains(w.getHash())
                && !data.getDiscoveredWaystones().contains(w.getHash());

        if (firstTime) {
            data.getSeenWaystones().add(w.getHash());
            storage.setDirty(); // persist the one-time discovery
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("a waypoint discovered").withStyle(ChatFormatting.DARK_AQUA)));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal(name).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true))));
        } else {
            // actionbar overlay (hybrid mapping uses the Yarn-style single-arg
            // sendOverlayMessage)
            player.sendOverlayMessage(Component.literal("◈ " + name).withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * Drop the transient latch on disconnect so the next login re-announces where
     * the player stands.
     */
    public static void forget(UUID id) {
        NEAR.remove(id);
    }
}
