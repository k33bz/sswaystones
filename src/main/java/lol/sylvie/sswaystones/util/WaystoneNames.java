/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.util;

// Cleans waystone names. Kept free of Minecraft types so it can be unit tested
public final class WaystoneNames {
    public static final int MAX_LENGTH = 32;
    private static final char SECTION_SIGN = '§';

    private WaystoneNames() {
    }

    /**
     * Drops legacy formatting codes and control characters, then caps the length.
     *
     * <p>
     * The Java anvil and dialog inputs already refuse '§', but Bedrock forms and {@code /waystonesettings apply} pass
     * text through untouched, so a name could carry colour codes, obfuscation or line breaks into everyone's viewer and
     * the floating name hologram. Every name is cleaned here, including names loaded from older saves.
     */
    public static String sanitize(String name) {
        if (name == null)
            return "";
        StringBuilder out = new StringBuilder(Math.min(name.length(), MAX_LENGTH));
        for (int i = 0; i < name.length() && out.length() < MAX_LENGTH; i++) {
            char c = name.charAt(i);
            if (c == SECTION_SIGN) {
                i++; // the code character that follows the sign goes too
                continue;
            }
            if (Character.isISOControl(c))
                continue;
            out.append(c);
        }
        // Never leave half a surrogate pair at the cut
        int len = out.length();
        if (len > 0 && Character.isHighSurrogate(out.charAt(len - 1)))
            out.setLength(len - 1);
        return out.toString();
    }
}
