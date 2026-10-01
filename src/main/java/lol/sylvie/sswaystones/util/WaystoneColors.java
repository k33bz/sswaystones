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

// Shared by the dialog selector, the viewer list and the hologram
public final class WaystoneColors {
    private WaystoneColors() {
    }

    public static ChatFormatting modeColor(AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> ChatFormatting.GRAY;
            case TEAM -> ChatFormatting.AQUA;
            case GLOBAL -> ChatFormatting.GREEN;
            case SERVER -> ChatFormatting.GOLD;
        };
    }

    // A team waystone takes its team's colour, everything else the mode colour
    public static TextColor nameColor(WaystoneRecord record, @Nullable Scoreboard scoreboard) {
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

    // On 26.1 a team colour is still a ChatFormatting
    private static @Nullable TextColor teamColor(PlayerTeam team) {
        return TextColor.fromLegacyFormat(team.getColor());
    }
}
