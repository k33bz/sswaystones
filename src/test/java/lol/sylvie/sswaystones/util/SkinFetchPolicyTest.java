/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

import static lol.sylvie.sswaystones.util.SkinFetchPolicy.REFRESH_MS;
import static lol.sylvie.sswaystones.util.SkinFetchPolicy.RETRY_MS;
import static lol.sylvie.sswaystones.util.SkinFetchPolicy.shouldFetch;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// The point of SkinCache is that icons never wait on Mojang and Mojang is never asked once per frame.
// These pin down how often a lookup may be queued
class SkinFetchPolicyTest {
    private static final long NOW = 1_000_000_000L;

    @Test
    void unknownOwnerIsFetched() {
        assertTrue(shouldFetch(false, false, false, 0L, NOW));
    }

    @Test
    void ownerAlreadyBeingFetchedIsNotQueuedAgain() {
        // Paging the viewer or a ticking icon display must not pile up duplicate lookups
        assertFalse(shouldFetch(true, true, false, NOW, NOW));
        assertFalse(shouldFetch(true, true, true, NOW - 10 * REFRESH_MS, NOW));
    }

    @Test
    void knownSkinIsReusedUntilItIsAnHourOld() {
        assertFalse(shouldFetch(true, false, true, NOW, NOW));
        assertFalse(shouldFetch(true, false, true, NOW - REFRESH_MS + 1, NOW));
        assertTrue(shouldFetch(true, false, true, NOW - REFRESH_MS, NOW));
    }

    @Test
    void failedLookupWaitsBeforeRetrying() {
        assertFalse(shouldFetch(true, false, false, NOW, NOW));
        assertFalse(shouldFetch(true, false, false, NOW - RETRY_MS + 1, NOW));
        assertTrue(shouldFetch(true, false, false, NOW - RETRY_MS, NOW));
    }

    @Test
    void failuresRetrySoonerThanSuccessesRefresh() {
        assertTrue(RETRY_MS < REFRESH_MS);
    }
}
