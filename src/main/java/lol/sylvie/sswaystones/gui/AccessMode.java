/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The single access mode (Private / Team / Global / Server-owned) the dialog
 * and Bedrock settings UIs present, collapsing the three underlying
 * {@code AccessSettings} fields into one mutually-exclusive choice. Kept free
 * of Minecraft imports so the mapping and permission logic are unit-testable
 * without a game runtime.
 */
public enum AccessMode {
    PRIVATE("private"), TEAM("team"), GLOBAL("global"), SERVER("server");

    private final String id;

    AccessMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * Parse a selector id back into a mode; unknown/null falls back to PRIVATE (the
     * safe floor).
     */
    public static AccessMode fromId(String id) {
        if (id != null) {
            for (AccessMode m : values())
                if (m.id.equalsIgnoreCase(id.trim()))
                    return m;
        }
        return PRIVATE;
    }

    /**
     * Map the waystone's current fields to the highest applicable mode, precedence
     * {@code server > global > team > private}. A redundant legacy combo (e.g.
     * global and team both set) normalizes down to the single displayed mode.
     */
    public static AccessMode fromSettings(boolean isServerOwned, boolean isGlobal, boolean hasTeam) {
        if (isServerOwned)
            return SERVER;
        if (isGlobal)
            return GLOBAL;
        if (hasTeam)
            return TEAM;
        return PRIVATE;
    }

    // Field values to store for this mode; every mode except TEAM clears the team.

    public boolean global() {
        return this == GLOBAL;
    }

    public boolean serverOwned() {
        return this == SERVER;
    }

    /**
     * The team string to store: the player's current team for TEAM, empty
     * otherwise.
     */
    public String team(String currentTeamName) {
        return this == TEAM ? (currentTeamName == null ? "" : currentTeamName) : "";
    }

    /**
     * The modes to offer in the selector, filtered by the player's permissions. The
     * waystone's current mode is always included so re-saving can never silently
     * downgrade a mode the player couldn't otherwise set.
     */
    public static List<AccessMode> availableModes(AccessMode current, boolean canTeam, boolean canGlobal,
            boolean canServer) {
        // A server-owned waystone is an admin resource: only someone who could SET server
        // (an admin) may move it to another mode. A non-admin — even the nominal owner —
        // sees it LOCKED on server, so they can't quietly reclaim it as private/global.
        if (current == SERVER && !canServer) {
            return List.of(SERVER);
        }
        List<AccessMode> modes = new ArrayList<>();
        modes.add(PRIVATE); // always
        if (canTeam || current == TEAM)
            modes.add(TEAM);
        if (canGlobal || current == GLOBAL)
            modes.add(GLOBAL);
        if (canServer || current == SERVER)
            modes.add(SERVER);
        return modes;
    }

    /**
     * Whether a player may apply this mode. The backend uses this as the
     * server-side gate so a hand-crafted {@code access:server} can't escalate;
     * PRIVATE is always allowed.
     */
    public boolean isAllowed(boolean canTeam, boolean canGlobal, boolean canServer) {
        return switch (this) {
            case PRIVATE -> true;
            case TEAM -> canTeam;
            case GLOBAL -> canGlobal;
            case SERVER -> canServer;
        };
    }

    /**
     * Who may edit a waystone's settings (rename, hide-name, access, icon). A
     * <em>server-owned</em> waystone is admin-controlled infrastructure: only an admin may
     * edit it — its nominal owner (a regular player) may not. Any other waystone is editable
     * by its owner or an admin. Kept here (Minecraft-free) so it's unit-testable.
     */
    public static boolean canEdit(boolean serverOwned, boolean isOwner, boolean isAdmin) {
        if (serverOwned)
            return isAdmin;
        return isOwner || isAdmin;
    }

    // ---- Marker head textures -------------------------------------------------
    // Publicly-reachable waystones get a recognisable globe head so players can
    // tell them apart at a glance in the viewer, whatever icon the owner picked.
    // Kept here (Minecraft-free) so the mapping stays unit-testable; the ItemStack
    // itself is built by AccessIcons.

    /** minecraft-heads.com head #102645 "Globe" — texture fc3dd6d8…, shown on GLOBAL waystones. */
    public static final String GLOBAL_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5t"
            + "aW5lY3JhZnQubmV0L3RleHR1cmUvZmMzZGQ2ZDgzNDBlY2M2NWIyY2I0OGYzNGQ5NTE0YjU2ZjczY2MyZDE1YTE1YWVhNWM3MTBiOTc2"
            + "YTNjMDA4ZiJ9fX0=";

    /** minecraft-heads.com head #3638 "Globe" — texture 48a013f0…, shown on SERVER-owned (admin) waystones. */
    public static final String SERVER_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5t"
            + "aW5lY3JhZnQubmV0L3RleHR1cmUvNDhhMDEzZjA0ZTg1OTQ4OGJkNDcxMTJmZjE2MTNmYTBmYTYyOThiMTVhYjZiYTNjYTVjZmQxNzE4"
            + "ZWZjNTg2MSJ9fX0=";

    /**
     * The marker head texture this mode forces, or {@code null} when the mode keeps
     * whatever icon the owner chose. Only the publicly-reachable modes (GLOBAL and
     * SERVER) override; PRIVATE and TEAM are personal, so the owner's icon stands.
     *
     * <p>The override is applied at render time rather than written into the record,
     * so demoting a waystone back to private/team restores the owner's original icon
     * with no stored state and no migration.
     */
    public String headTexture() {
        return switch (this) {
            case GLOBAL -> GLOBAL_HEAD_TEXTURE;
            case SERVER -> SERVER_HEAD_TEXTURE;
            case PRIVATE, TEAM -> null;
        };
    }
}
