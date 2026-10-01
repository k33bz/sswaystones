/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import java.util.ArrayList;
import java.util.List;

// The one access level shown by the dialog and the Bedrock form, in place of the
// three AccessSettings flags. No Minecraft imports so it can be unit tested.
public enum AccessMode {
    PRIVATE("private"), TEAM("team"), GLOBAL("global"), SERVER("server");

    // Globe heads from minecraft-heads.com: #102645 for global, #3638 for
    // server-owned
    public static final String GLOBAL_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5t"
            + "aW5lY3JhZnQubmV0L3RleHR1cmUvZmMzZGQ2ZDgzNDBlY2M2NWIyY2I0OGYzNGQ5NTE0YjU2ZjczY2MyZDE1YTE1YWVhNWM3MTBiOTc2"
            + "YTNjMDA4ZiJ9fX0=";

    public static final String SERVER_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5t"
            + "aW5lY3JhZnQubmV0L3RleHR1cmUvNDhhMDEzZjA0ZTg1OTQ4OGJkNDcxMTJmZjE2MTNmYTBmYTYyOThiMTVhYjZiYTNjYTVjZmQxNzE4"
            + "ZWZjNTg2MSJ9fX0=";

    private final String id;

    AccessMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AccessMode fromId(String id) {
        if (id != null) {
            for (AccessMode mode : values())
                if (mode.id.equalsIgnoreCase(id.trim()))
                    return mode;
        }
        return PRIVATE;
    }

    // server > global > team > private
    public static AccessMode fromSettings(boolean isServerOwned, boolean isGlobal, boolean hasTeam) {
        if (isServerOwned)
            return SERVER;
        if (isGlobal)
            return GLOBAL;
        if (hasTeam)
            return TEAM;
        return PRIVATE;
    }

    public boolean global() {
        return this == GLOBAL;
    }

    public boolean serverOwned() {
        return this == SERVER;
    }

    public String team(String currentTeamName) {
        return this == TEAM && currentTeamName != null ? currentTeamName : "";
    }

    // The current mode is always offered so a re-save can't downgrade it. A
    // server-owned waystone stays locked for anyone who couldn't set server.
    public static List<AccessMode> availableModes(AccessMode current, boolean canTeam, boolean canGlobal,
            boolean canServer) {
        if (current == SERVER && !canServer)
            return List.of(SERVER);
        List<AccessMode> modes = new ArrayList<>();
        modes.add(PRIVATE);
        if (canTeam || current == TEAM)
            modes.add(TEAM);
        if (canGlobal || current == GLOBAL)
            modes.add(GLOBAL);
        if (canServer || current == SERVER)
            modes.add(SERVER);
        return modes;
    }

    public boolean isAllowed(boolean canTeam, boolean canGlobal, boolean canServer) {
        return switch (this) {
            case PRIVATE -> true;
            case TEAM -> canTeam;
            case GLOBAL -> canGlobal;
            case SERVER -> canServer;
        };
    }

    // Server-owned waystones belong to the server, not to whoever placed them
    public static boolean canEdit(boolean serverOwned, boolean isOwner, boolean isAdmin) {
        return serverOwned ? isAdmin : isOwner || isAdmin;
    }

    // Global waystones always show the globe; server-owned ones only until an admin
    // picks an icon
    public static boolean usesMarkerIcon(AccessMode mode, boolean hasCustomIcon) {
        return switch (mode) {
            case GLOBAL -> true;
            case SERVER -> !hasCustomIcon;
            case PRIVATE, TEAM -> false;
        };
    }

    public String headTexture() {
        return switch (this) {
            case GLOBAL -> GLOBAL_HEAD_TEXTURE;
            case SERVER -> SERVER_HEAD_TEXTURE;
            case PRIVATE, TEAM -> null;
        };
    }
}
