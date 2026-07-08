/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The single access mode (Private / Team / Global / Server-owned) the dialog and Bedrock settings
 * UIs present, collapsing the three underlying {@code AccessSettings} fields into one
 * mutually-exclusive choice. Kept free of Minecraft imports so the mapping and permission logic
 * are unit-testable without a game runtime.
 */
public enum AccessMode {
    PRIVATE("private"),
    TEAM("team"),
    GLOBAL("global"),
    SERVER("server");

    private final String id;

    AccessMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Parse a selector id back into a mode; unknown/null falls back to PRIVATE (the safe floor). */
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
     * {@code server > global > team > private}. A redundant legacy combo (e.g. global and team both
     * set) normalizes down to the single displayed mode.
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

    // Field values to store for this mode; every mode other than TEAM clears the team.

    public boolean global() {
        return this == GLOBAL;
    }

    public boolean serverOwned() {
        return this == SERVER;
    }

    /** The team string to store: the player's current team for TEAM, empty otherwise. */
    public String team(String currentTeamName) {
        return this == TEAM ? (currentTeamName == null ? "" : currentTeamName) : "";
    }

    /**
     * The modes to offer in the selector, filtered by the player's permissions. The waystone's
     * current mode is always included so re-saving can never silently downgrade a mode the player
     * couldn't otherwise set.
     */
    public static List<AccessMode> availableModes(AccessMode current, boolean canTeam, boolean canGlobal,
            boolean canServer) {
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
     * Whether a player may apply this mode. The backend uses this as the server-side gate so a
     * hand-crafted {@code access:server} can't escalate; PRIVATE is always allowed.
     */
    public boolean isAllowed(boolean canTeam, boolean canGlobal, boolean canServer) {
        return switch (this) {
            case PRIVATE -> true;
            case TEAM -> canTeam;
            case GLOBAL -> canGlobal;
            case SERVER -> canServer;
        };
    }
}
