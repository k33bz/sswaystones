/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.command;

import java.util.Optional;
import lol.sylvie.sswaystones.gui.AccessMode;

// The tail of /waystonesettings apply:
//   <hash> [access:<mode>] [hidename:<bool>] [global:<bool>] [team:<bool>] [server:<bool>] [name:<rest>]
// name: runs to the end of the line so it may contain spaces and colons. "-" leaves
// a value unchanged. access: replaces the per-field flags when both are given.
// No Brigadier imports so the parsing can be unit tested.
public final class ApplyArgs {
    public static final String SENTINEL = "-";

    private final String hash;
    private final Optional<String> name;
    private final Optional<String> hidename;
    private final Optional<AccessMode> accessMode;
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

        // Take name: off the end first so its text can't be read as other keys
        Optional<String> name = Optional.empty();
        int nameIdx = indexOfKey(rest, "name");
        if (nameIdx >= 0) {
            name = Optional.of(rest.substring(nameIdx + "name:".length()));
            rest = rest.substring(0, nameIdx).trim();
        }

        Optional<String> access = shortToken(rest, "access");
        Optional<String> hidename = shortToken(rest, "hidename");
        Optional<String> global = shortToken(rest, "global");
        Optional<String> team = shortToken(rest, "team");
        Optional<String> server = shortToken(rest, "server");

        Optional<AccessMode> accessMode = Optional.empty();
        if (isSet(access.orElse(null))) {
            accessMode = Optional.of(AccessMode.fromId(access.get()));
            global = Optional.empty();
            team = Optional.empty();
            server = Optional.empty();
        }

        return new ApplyArgs(hash, name, hidename, accessMode, global, team, server);
    }

    public String hash() {
        return hash;
    }

    public Optional<String> newName() {
        return name.filter(ApplyArgs::isSet);
    }

    public Optional<AccessMode> accessMode() {
        return accessMode;
    }

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

    static boolean isSet(String v) {
        return v != null && !v.equals(SENTINEL);
    }

    static boolean parseBool(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "on".equalsIgnoreCase(v);
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++)
            if (Character.isWhitespace(s.charAt(i)))
                return i;
        return -1;
    }

    // "key:" at the start or after whitespace
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

    // The value of "key:" up to the next whitespace
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
