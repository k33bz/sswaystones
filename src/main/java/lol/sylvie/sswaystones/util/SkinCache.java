/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.services.ProfileResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lol.sylvie.sswaystones.Waystones;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Waystone owners' skins, fetched from Mojang on a background thread.
 *
 * <p>
 * Head icons used to call {@code SessionService.fetchProfile} on the server thread, a blocking HTTP request per icon.
 * Paging through the viewer, or a tick with {@code physical_icon_display} on, could stall the whole server for as
 * long as Mojang took to answer. Now the first request for an owner queues one lookup and returns a plain head; the
 * skin appears the next time the icon is built.
 */
public final class SkinCache {
    private record Entry(@Nullable GameProfile profile, long at, boolean pending) {
    }

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    // One daemon thread: lookups are rare, and it can never hold up a server shutdown
    private static final ExecutorService FETCHER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sswaystones-skin-fetch");
        thread.setDaemon(true);
        return thread;
    });

    private SkinCache() {
    }

    // Never blocks. Returns the cached profile with its skin, or a bare one while the lookup runs
    public static GameProfile profileFor(MinecraftServer server, UUID id, String name) {
        long now = System.currentTimeMillis();
        Entry entry = CACHE.get(id);
        @Nullable
        GameProfile known = entry == null ? null : entry.profile();

        if (SkinFetchPolicy.shouldFetch(entry != null, entry != null && entry.pending(), known != null,
                entry == null ? 0L : entry.at(), now)) {
            CACHE.put(id, new Entry(known, now, true));
            MinecraftSessionService service = server.services().sessionService();
            FETCHER.execute(() -> fetch(service, id, known));
        }

        return known != null ? known : new GameProfile(id, name);
    }

    private static void fetch(MinecraftSessionService service, UUID id, @Nullable GameProfile previous) {
        @Nullable
        GameProfile result = previous; // A failed refresh keeps the skin we already had
        try {
            ProfileResult fetched = service.fetchProfile(id, false);
            if (fetched != null)
                result = fetched.profile();
        } catch (RuntimeException e) {
            Waystones.LOGGER.debug("Could not fetch the skin for {}", id, e);
        } finally {
            // Always clear pending, or a failed lookup would never be retried
            CACHE.put(id, new Entry(result, System.currentTimeMillis(), false));
        }
    }
}
