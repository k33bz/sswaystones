/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.command;

import java.util.Optional;
import lol.sylvie.sswaystones.gui.AccessMode;

/**
 * Parsed argument tail of {@code /waystonesettings apply}. Kept free of
 * Minecraft/Brigadier imports so the tricky cases (a name containing a colon,
 * the {@code "-"} leave-unchanged sentinel, {@code access:} vs the legacy
 * per-field tokens) are unit-testable.
 *
 * <p>
 * Grammar (one greedy string):
 * {@code <hash> [key:value ...] [name:<free text to end>]}. The short-value
 * keys are {@code access}, {@code global}, {@code team}, {@code server},
 * {@code hidename}. {@code name} may contain spaces and colons, so it always
 * comes last and consumes the rest of the string. When {@code access:} is
 * present it is authoritative and the legacy per-field
 * {@code global:/team:/server:} tokens are ignored, so the two forms never
 * fight.
 */
public final class ApplyArgs {
    // A value that is present but should be left unchanged.
    public static final String SENTINEL = "-";

    private final String hash;
    private final Optional<String> name; // present() only when a name: token was given (may be "-")
    private final Optional<String> hidename; // raw "true"/"false"/"-" if present
    private final Optional<AccessMode> accessMode; // present iff access: given (and parseable)
    // Legacy per-field tokens, present only when access: is ABSENT.
    private final Optional<String> global;
    private final Optional<String> team;
    private final Optional<String> server;

    private ApplyArgs(String hash, Optional<String> name, Optional<String> hidename, Optional<AccessMode> accessMode,
            Optional<String> global, Optional<String> team, Optional<String> server) {
        this.hash = hash;
        this.name = name;
        this.hidename = hidename;
        this.accessMode = accessMode;
        this.global = global;
        this.team = team;
        this.server = server;
    }

    public static ApplyArgs parse(String raw) {
        String s = raw == null ? "" : raw.trim();

        // hash = leading bare word.
        String hash;
        String rest;
        int sp = indexOfWhitespace(s);
        if (sp < 0) {
            hash = s;
            rest = "";
        } else {
            hash = s.substring(0, sp);
            rest = s.substring(sp + 1).trim();
        }

        // Split name: off first so its free text (spaces/colons) can't be mis-parsed
        // as other keys.
        Optional<String> name = Optional.empty();
        int nameIdx = indexOfKey(rest, "name");
        if (nameIdx >= 0) {
            name = Optional.of(rest.substring(nameIdx + "name:".length())); // to end of string, verbatim
            rest = rest.substring(0, nameIdx).trim(); // the short-value keys live before name:
        }

        Optional<String> access = shortToken(rest, "access");
        Optional<String> hidename = shortToken(rest, "hidename");
        Optional<String> global = shortToken(rest, "global");
        Optional<String> team = shortToken(rest, "team");
        Optional<String> server = shortToken(rest, "server");

        // access: wins — ignore the legacy per-field tokens entirely when it's present.
        Optional<AccessMode> accessMode = Optional.empty();
        if (isSet(access.orElse(null))) {
            accessMode = Optional.of(AccessMode.fromId(access.get()));
            global = Optional.empty();
            team = Optional.empty();
            server = Optional.empty();
        }

        return new ApplyArgs(hash, name, hidename, accessMode, global, team, server);
    }

    // --- accessors ---
    public String hash() {
        return hash;
    }

    /**
     * The new name if a name: token was given AND it isn't the leave-unchanged
     * sentinel.
     */
    public Optional<String> newName() {
        return name.filter(ApplyArgs::isSet);
    }

    /** The chosen access mode, if the single access: form was used. */
    public Optional<AccessMode> accessMode() {
        return accessMode;
    }

    /**
     * Legacy per-field global (true/false), present only when access: was absent
     * and it was set.
     */
    public Optional<Boolean> global() {
        return global.filter(ApplyArgs::isSet).map(ApplyArgs::parseBool);
    }

    public Optional<Boolean> team() {
        return team.filter(ApplyArgs::isSet).map(ApplyArgs::parseBool);
    }

    public Optional<Boolean> server() {
        return server.filter(ApplyArgs::isSet).map(ApplyArgs::parseBool);
    }

    public Optional<Boolean> hideName() {
        return hidename.filter(ApplyArgs::isSet).map(ApplyArgs::parseBool);
    }

    // --- helpers (package-visible for tests) ---

    /**
     * A value is "set" (should be applied) when present and not the "-"
     * leave-unchanged sentinel.
     */
    static boolean isSet(String v) {
        return v != null && !v.equals(SENTINEL);
    }

    static boolean parseBool(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v);
    }

    // First whitespace index, or -1.
    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++)
            if (Character.isWhitespace(s.charAt(i)))
                return i;
        return -1;
    }

    // Index of a "key:" occurrence at a token boundary (start or after
    // whitespace), or -1.
    private static int indexOfKey(String s, String key) {
        String needle = key + ":";
        int from = 0;
        while (true) {
            int i = s.indexOf(needle, from);
            if (i < 0)
                return -1;
            if (i == 0 || Character.isWhitespace(s.charAt(i - 1)))
                return i;
            from = i + 1;
        }
    }

    // Pull a short (no-spaces) token value: "key:" up to the next
    // whitespace or end-of-string.
    private static Optional<String> shortToken(String s, String key) {
        int i = indexOfKey(s, key);
        if (i < 0)
            return Optional.empty();
        int valStart = i + key.length() + 1;
        int end = valStart;
        while (end < s.length() && !Character.isWhitespace(s.charAt(end)))
            end++;
        return Optional.of(s.substring(valStart, end));
    }
}
