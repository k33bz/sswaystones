/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * The single "Access" mode the native dialog presents as ONE cycling selector (Private / Team /
 * Global / Server-owned), collapsing the three underlying {@code AccessSettings} fields
 * ({@code global} + {@code server} booleans, {@code team} string) into one mutually-exclusive
 * choice. This is a PURE presentation/mapping helper — the data model, its Codec, and the frozen
 * sgui menu (which keeps three separate toggles) are unchanged; this only decides which mode the
 * dialog shows and how the chosen mode maps back onto the same setter calls.
 *
 * <p>
 * Kept dependency-free (no Minecraft imports) so the mapping, precedence, and permission-filtering
 * logic are unit-testable without a game runtime.
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
     * Map the waystone's current fields to the highest applicable mode. Precedence
     * {@code server > global > team > private} — this also normalizes a legacy redundant combo
     * (e.g. both global and team set) down to the single displayed mode.
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

    // --- field targets for a chosen mode (mutually exclusive) ---
    // The three setters the backend must call for this mode. teamName is used only by TEAM; every
    // other mode clears the team to "".

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
     * Which modes to OFFER in the selector, filtered by the player's permissions, but ALWAYS
     * including the waystone's current mode so re-saving can never silently downgrade a mode the
     * player can't otherwise set. Order is fixed Private, Team, Global, Server for a stable cycle.
     *
     * @param current the waystone's current mode (always included)
     * @param canTeam player may set team (perm + on a scoreboard team)
     * @param canGlobal player may set global
     * @param canServer player may set server-owned (admin)
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
     * Whether a player may APPLY this mode, given their permissions. The backend uses this as the
     * server-side gate so a hand-crafted {@code access:server} can't escalate. PRIVATE is always
     * allowed; TEAM/GLOBAL/SERVER require the matching permission.
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
