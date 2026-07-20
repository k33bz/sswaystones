/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic guards for the collapsed "Access" selector: the mode -> fields
 * mapping (each mode sets exactly the right booleans+team), the fields ->
 * initial-mode precedence (incl. legacy-combo normalization), and the
 * per-permission option filtering (server only for admins). A regression in any
 * of these would silently mis-set access on a live server, so each is asserted
 * here.
 */
class AccessModeTest {

    // --- mode -> fields (mutually exclusive) ---

    @Test
    void privateClearsEverything() {
        assertFalse(AccessMode.PRIVATE.global());
        assertFalse(AccessMode.PRIVATE.serverOwned());
        assertEquals("", AccessMode.PRIVATE.team("red"));
    }

    @Test
    void teamSetsTeamOnlyToCurrentTeamName() {
        assertFalse(AccessMode.TEAM.global());
        assertFalse(AccessMode.TEAM.serverOwned());
        assertEquals("red", AccessMode.TEAM.team("red"));
        assertEquals("", AccessMode.TEAM.team(null)); // no team name -> empty, never null
    }

    @Test
    void globalSetsGlobalOnly() {
        assertTrue(AccessMode.GLOBAL.global());
        assertFalse(AccessMode.GLOBAL.serverOwned());
        assertEquals("", AccessMode.GLOBAL.team("red"));
    }

    @Test
    void serverSetsServerOwnedOnly() {
        assertFalse(AccessMode.SERVER.global());
        assertTrue(AccessMode.SERVER.serverOwned());
        assertEquals("", AccessMode.SERVER.team("red"));
    }

    // --- fields -> initial mode (precedence server > global > team > private) ---

    @Test
    void freshWaystoneIsPrivate() {
        assertEquals(AccessMode.PRIVATE, AccessMode.fromSettings(false, false, false));
    }

    @Test
    void teamOnlyMapsToTeam() {
        assertEquals(AccessMode.TEAM, AccessMode.fromSettings(false, false, true));
    }

    @Test
    void globalMapsToGlobal() {
        assertEquals(AccessMode.GLOBAL, AccessMode.fromSettings(false, true, false));
    }

    @Test
    void serverOwnedWinsOverEverything() {
        // server is highest precedence, even if global/team also set
        assertEquals(AccessMode.SERVER, AccessMode.fromSettings(true, true, true));
        assertEquals(AccessMode.SERVER, AccessMode.fromSettings(true, false, false));
    }

    @Test
    void globalWinsOverTeam_legacyComboNormalizes() {
        // a legacy redundant global+team combo collapses to the displayed mode
        assertEquals(AccessMode.GLOBAL, AccessMode.fromSettings(false, true, true));
    }

    // --- round trip: fromSettings then apply -> stable mode ---

    @Test
    void modeRoundTripsThroughFields() {
        for (AccessMode m : AccessMode.values()) {
            AccessMode back = AccessMode.fromSettings(m.serverOwned(), m.global(), !m.team("t").isEmpty());
            assertEquals(m, back, m + " should round-trip through its field targets");
        }
    }

    // --- option filtering by permission (always include current mode) ---

    @Test
    void privateAlwaysOffered() {
        List<AccessMode> modes = AccessMode.availableModes(AccessMode.PRIVATE, false, false, false);
        assertEquals(List.of(AccessMode.PRIVATE), modes);
    }

    @Test
    void serverOptionOnlyForAdmins() {
        // non-admin (canServer=false), not currently server-owned -> NO server option
        List<AccessMode> nonAdmin = AccessMode.availableModes(AccessMode.PRIVATE, true, true, false);
        assertFalse(nonAdmin.contains(AccessMode.SERVER), "non-admin must not get the server option");
        assertTrue(nonAdmin.contains(AccessMode.GLOBAL));
        assertTrue(nonAdmin.contains(AccessMode.TEAM));

        // admin -> server option present
        List<AccessMode> admin = AccessMode.availableModes(AccessMode.PRIVATE, true, true, true);
        assertTrue(admin.contains(AccessMode.SERVER), "admin gets the server option");
    }

    @Test
    void teamAndGlobalGatedByTheirPerms() {
        List<AccessMode> none = AccessMode.availableModes(AccessMode.PRIVATE, false, false, false);
        assertFalse(none.contains(AccessMode.TEAM));
        assertFalse(none.contains(AccessMode.GLOBAL));

        List<AccessMode> teamOnly = AccessMode.availableModes(AccessMode.PRIVATE, true, false, false);
        assertTrue(teamOnly.contains(AccessMode.TEAM));
        assertFalse(teamOnly.contains(AccessMode.GLOBAL));
    }

    @Test
    void currentModeAlwaysIncludedEvenWithoutPerm() {
        // a waystone already server-owned keeps the server option for a non-admin
        // viewer, so re-saving can't silently downgrade it
        List<AccessMode> modes = AccessMode.availableModes(AccessMode.SERVER, false, false, false);
        assertTrue(modes.contains(AccessMode.SERVER), "current mode must always be offered");
        assertEquals(List.of(AccessMode.PRIVATE, AccessMode.SERVER), modes);
    }

    @Test
    void optionOrderIsStablePrivateTeamGlobalServer() {
        List<AccessMode> all = AccessMode.availableModes(AccessMode.PRIVATE, true, true, true);
        assertEquals(List.of(AccessMode.PRIVATE, AccessMode.TEAM, AccessMode.GLOBAL, AccessMode.SERVER), all);
    }

    // --- server-side apply gate (isAllowed) — the anti-escalation check ---

    @Test
    void isAllowedGatesByPermission() {
        // a non-admin cannot APPLY server even if they somehow submit access:server
        assertFalse(AccessMode.SERVER.isAllowed(true, true, false));
        assertTrue(AccessMode.SERVER.isAllowed(false, false, true));
        // private is always allowed
        assertTrue(AccessMode.PRIVATE.isAllowed(false, false, false));
        // team/global gated
        assertFalse(AccessMode.TEAM.isAllowed(false, true, true));
        assertFalse(AccessMode.GLOBAL.isAllowed(true, false, true));
    }

    // --- id parsing ---

    @Test
    void fromIdParsesKnownIdsAndFallsBackToPrivate() {
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId("private"));
        assertEquals(AccessMode.TEAM, AccessMode.fromId("team"));
        assertEquals(AccessMode.GLOBAL, AccessMode.fromId("GLOBAL"));
        assertEquals(AccessMode.SERVER, AccessMode.fromId(" server "));
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId(null));
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId("bogus"));
    }

    // --- marker head textures (public modes only) ---

    @Test
    void onlyPubliclyReachableModesForceAMarkerHead() {
        // Personal modes keep whatever icon the owner chose.
        assertNull(AccessMode.PRIVATE.headTexture());
        assertNull(AccessMode.TEAM.headTexture());
        // Public ones override it with their globe marker.
        assertEquals(AccessMode.GLOBAL_HEAD_TEXTURE, AccessMode.GLOBAL.headTexture());
        assertEquals(AccessMode.SERVER_HEAD_TEXTURE, AccessMode.SERVER.headTexture());
    }

    @Test
    void globalAndServerUseDistinctHeads() {
        assertNotEquals(AccessMode.GLOBAL.headTexture(), AccessMode.SERVER.headTexture());
    }

    /**
     * The textures are base64 skin blobs pinned to specific minecraft-heads
     * entries; decoding them guards against a truncated/re-wrapped paste, which
     * would otherwise only show up in-game as a blank steve head.
     */
    @Test
    void headTexturesDecodeToTheExpectedSkinUrls() {
        String global = new String(java.util.Base64.getDecoder().decode(AccessMode.GLOBAL_HEAD_TEXTURE),
                java.nio.charset.StandardCharsets.UTF_8);
        String server = new String(java.util.Base64.getDecoder().decode(AccessMode.SERVER_HEAD_TEXTURE),
                java.nio.charset.StandardCharsets.UTF_8);
        // head #102645
        assertTrue(global.contains("fc3dd6d8340ecc65b2cb48f34d9514b56f73cc2d15a15aea5c710b976a3c008f"), global);
        // head #3638
        assertTrue(server.contains("48a013f04e859488bd47112ff1613fa0fa6298b15ab6ba3ca5cfd1718efc5861"), server);
    }
}
