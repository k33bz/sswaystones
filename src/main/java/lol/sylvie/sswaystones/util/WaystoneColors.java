/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

import lol.sylvie.sswaystones.gui.AccessMode;
import lol.sylvie.sswaystones.storage.WaystoneRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * One place for the colours a waystone is shown in, so the settings-dialog selector,
 * the viewer list, and the in-world hologram all agree.
 *
 * <p>The access-mode palette (private/team/global/server) is the generic fallback; a
 * <em>team</em> waystone additionally resolves to its team's actual colour, so a waystone
 * named in the list matches the teal/red/etc. it floats in the world.
 */
public final class WaystoneColors {
    private WaystoneColors() {
    }

    /** The generic colour for an access mode — used by the dialog selector and as the fallback. */
    public static ChatFormatting modeColor(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> ChatFormatting.GRAY;
            case TEAM -> ChatFormatting.AQUA;
            case GLOBAL -> ChatFormatting.GREEN;
            case SERVER -> ChatFormatting.GOLD;
        };
    }

    /**
     * The colour a waystone's <em>name</em> is drawn in: a team waystone's real team colour
     * (matching the in-world hologram), otherwise the access-mode palette. Returns null only
     * if the palette entry has no colour (never, for the four modes above).
     */
    public static @Nullable TextColor nameColor(WaystoneRecord record, @Nullable Scoreboard scoreboard) {
        WaystoneRecord.AccessSettings access = record.getAccessSettings();
        AccessMode mode = AccessMode.fromSettings(access.isServerOwned(), access.isGlobal(), access.hasTeam());
        if (mode == AccessMode.TEAM && scoreboard != null) {
            PlayerTeam team = scoreboard.getPlayerTeam(access.getTeam());
            if (team != null) {
                TextColor teamColor = teamColor(team);
                if (teamColor != null)
                    return teamColor;
            }
        }
        return TextColor.fromLegacyFormat(modeColor(mode));
    }

    // Branch-specific: on 26.1 a team's colour is a ChatFormatting (26.2 = Optional<TeamColor>).
    private static @Nullable TextColor teamColor(PlayerTeam team) {
        return TextColor.fromLegacyFormat(team.getColor());
    }
}
