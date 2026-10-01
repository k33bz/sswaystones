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

class AccessModeTest {
    private static AccessMode.Permissions perms(boolean team, boolean global, boolean server) {
        return new AccessMode.Permissions(team, global, server);
    }

    // Mode to fields

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

    // Fields to mode

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
        // server beats global and team
        assertEquals(AccessMode.SERVER, AccessMode.fromSettings(true, true, true));
        assertEquals(AccessMode.SERVER, AccessMode.fromSettings(true, false, false));
    }

    @Test
    void globalWinsOverTeam_legacyComboNormalizes() {
        // an old global+team save collapses to global
        assertEquals(AccessMode.GLOBAL, AccessMode.fromSettings(false, true, true));
    }

    // Round trip

    @Test
    void modeRoundTripsThroughFields() {
        for (AccessMode m : AccessMode.values()) {
            AccessMode back = AccessMode.fromSettings(m.serverOwned(), m.global(), !m.team("t").isEmpty());
            assertEquals(m, back, m + " should round-trip through its field targets");
        }
    }

    // Which modes are offered

    @Test
    void privateAlwaysOffered() {
        List<AccessMode> modes = AccessMode.availableModes(AccessMode.PRIVATE, perms(false, false, false));
        assertEquals(List.of(AccessMode.PRIVATE), modes);
    }

    @Test
    void serverOptionOnlyForAdmins() {
        // not an admin, not currently server-owned
        List<AccessMode> nonAdmin = AccessMode.availableModes(AccessMode.PRIVATE, perms(true, true, false));
        assertFalse(nonAdmin.contains(AccessMode.SERVER), "non-admin must not get the server option");
        assertTrue(nonAdmin.contains(AccessMode.GLOBAL));
        assertTrue(nonAdmin.contains(AccessMode.TEAM));

        // admin
        List<AccessMode> admin = AccessMode.availableModes(AccessMode.PRIVATE, perms(true, true, true));
        assertTrue(admin.contains(AccessMode.SERVER), "admin gets the server option");
    }

    @Test
    void teamAndGlobalGatedByTheirPerms() {
        List<AccessMode> none = AccessMode.availableModes(AccessMode.PRIVATE, perms(false, false, false));
        assertFalse(none.contains(AccessMode.TEAM));
        assertFalse(none.contains(AccessMode.GLOBAL));

        List<AccessMode> teamOnly = AccessMode.availableModes(AccessMode.PRIVATE, perms(true, false, false));
        assertTrue(teamOnly.contains(AccessMode.TEAM));
        assertFalse(teamOnly.contains(AccessMode.GLOBAL));
    }

    @Test
    void serverOwnedIsLockedForNonAdmins() {
        // A non-admin can't demote a server-owned waystone, even as its owner
        assertEquals(List.of(AccessMode.SERVER),
                AccessMode.availableModes(AccessMode.SERVER, perms(false, false, false)));
        assertEquals(List.of(AccessMode.SERVER),
                AccessMode.availableModes(AccessMode.SERVER, perms(true, true, false)));
    }

    @Test
    void adminMayMoveAServerOwnedWaystone() {
        // An admin can
        List<AccessMode> admin = AccessMode.availableModes(AccessMode.SERVER, perms(false, true, true));
        assertTrue(admin.contains(AccessMode.PRIVATE), "admin can demote to private");
        assertTrue(admin.contains(AccessMode.SERVER));
    }

    @Test
    void nonServerCurrentModeStillIncludedWithoutPerm() {
        // Only server-owned locks. A global waystone keeps global on the menu for its
        // owner even without the permission, so a re-save can't drop it.
        List<AccessMode> modes = AccessMode.availableModes(AccessMode.GLOBAL, perms(false, false, false));
        assertTrue(modes.contains(AccessMode.GLOBAL), "current non-server mode stays offered");
        assertTrue(modes.contains(AccessMode.PRIVATE));
    }

    @Test
    void optionOrderIsStablePrivateTeamGlobalServer() {
        List<AccessMode> all = AccessMode.availableModes(AccessMode.PRIVATE, perms(true, true, true));
        assertEquals(List.of(AccessMode.PRIVATE, AccessMode.TEAM, AccessMode.GLOBAL, AccessMode.SERVER), all);
    }

    // Apply gate

    @Test
    void isAllowedGatesByPermission() {
        // a non-admin can't submit access:server by hand
        assertFalse(AccessMode.SERVER.isAllowed(perms(true, true, false)));
        assertTrue(AccessMode.SERVER.isAllowed(perms(false, false, true)));
        // private is always allowed
        assertTrue(AccessMode.PRIVATE.isAllowed(perms(false, false, false)));
        // team/global gated
        assertFalse(AccessMode.TEAM.isAllowed(perms(false, true, true)));
        assertFalse(AccessMode.GLOBAL.isAllowed(perms(true, false, true)));
    }

    // Ids

    @Test
    void fromIdParsesKnownIdsAndFallsBackToPrivate() {
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId("private"));
        assertEquals(AccessMode.TEAM, AccessMode.fromId("team"));
        assertEquals(AccessMode.GLOBAL, AccessMode.fromId("GLOBAL"));
        assertEquals(AccessMode.SERVER, AccessMode.fromId(" server "));
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId(null));
        assertEquals(AccessMode.PRIVATE, AccessMode.fromId("bogus"));
    }

    // Marker heads

    @Test
    void onlyPubliclyReachableModesForceAMarkerHead() {
        // personal modes keep the owner's icon
        assertNull(AccessMode.PRIVATE.headTexture());
        assertNull(AccessMode.TEAM.headTexture());
        // public ones get the globe
        assertEquals(AccessMode.GLOBAL_HEAD_TEXTURE, AccessMode.GLOBAL.headTexture());
        assertEquals(AccessMode.SERVER_HEAD_TEXTURE, AccessMode.SERVER.headTexture());
    }

    @Test
    void globalAndServerUseDistinctHeads() {
        assertNotEquals(AccessMode.GLOBAL.headTexture(), AccessMode.SERVER.headTexture());
    }

    // Who may edit

    @Test
    void serverOwnedIsEditableOnlyByAdmins() {
        // being the owner is not enough on a server-owned waystone
        assertFalse(AccessMode.canEdit(true, true, false), "owner (non-admin) cannot edit server waystone");
        assertFalse(AccessMode.canEdit(true, false, false));
        assertTrue(AccessMode.canEdit(true, false, true), "admin can edit server waystone");
        assertTrue(AccessMode.canEdit(true, true, true));
    }

    @Test
    void ordinaryWaystoneIsEditableByOwnerOrAdmin() {
        assertTrue(AccessMode.canEdit(false, true, false), "owner edits their own waystone");
        assertTrue(AccessMode.canEdit(false, false, true), "admin edits any waystone");
        assertFalse(AccessMode.canEdit(false, false, false), "a stranger cannot edit");
    }

    // Marker vs chosen icon

    @Test
    void globalAlwaysWearsTheMarker() {
        // with or without an icon of its own
        assertTrue(AccessMode.usesMarkerIcon(AccessMode.GLOBAL, false));
        assertTrue(AccessMode.usesMarkerIcon(AccessMode.GLOBAL, true));
    }

    @Test
    void serverOwnedIsAdminCurated() {
        // a chosen icon wins, else the admin globe
        assertFalse(AccessMode.usesMarkerIcon(AccessMode.SERVER, true), "custom icon wins on a server waystone");
        assertTrue(AccessMode.usesMarkerIcon(AccessMode.SERVER, false), "no icon -> admin globe");
    }

    @Test
    void privateAndTeamNeverUseTheMarker() {
        for (boolean custom : new boolean[]{true, false}) {
            assertFalse(AccessMode.usesMarkerIcon(AccessMode.PRIVATE, custom));
            assertFalse(AccessMode.usesMarkerIcon(AccessMode.TEAM, custom));
        }
    }

    // A truncated paste would only show up in game as a blank Steve head
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
