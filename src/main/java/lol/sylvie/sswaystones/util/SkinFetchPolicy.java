/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

// When SkinCache asks Mojang for a waystone owner's skin. Kept free of Minecraft types so it can be unit tested
public final class SkinFetchPolicy {
    // A skin we have is refreshed hourly, so a changed skin shows up eventually
    public static final long REFRESH_MS = 60L * 60L * 1000L;
    // A failed lookup (Mojang down, rate limited, offline-mode UUID) is retried every five minutes, not every frame
    public static final long RETRY_MS = 5L * 60L * 1000L;

    private SkinFetchPolicy() {
    }

    /**
     * @param cached
     *            there is an entry for this owner at all
     * @param pending
     *            a lookup for this owner is already queued or running
     * @param hasSkin
     *            the entry holds a fetched profile
     * @param fetchedAt
     *            when the entry was last written, in epoch millis
     * @param now
     *            the current time, in epoch millis
     */
    public static boolean shouldFetch(boolean cached, boolean pending, boolean hasSkin, long fetchedAt, long now) {
        if (!cached)
            return true;
        if (pending)
            return false;
        long age = now - fetchedAt;
        return age >= (hasSkin ? REFRESH_MS : RETRY_MS);
    }
}
