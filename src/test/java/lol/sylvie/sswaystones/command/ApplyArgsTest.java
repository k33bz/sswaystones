/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lol.sylvie.sswaystones.gui.AccessMode;
import org.junit.jupiter.api.Test;

/**
 * Parser guards for the {@code /waystonesettings apply} tail. The tricky cases — a name containing a
 * colon, the {@code "-"} leave-unchanged sentinel, and {@code access:} taking precedence over the
 * legacy per-field tokens — are locked down here so a regression fails fast rather than silently
 * mangling a name or fighting itself over access.
 */
class ApplyArgsTest {

    @Test
    void parsesHashAndAllFields() {
        ApplyArgs a = ApplyArgs.parse("abc123 access:global hidename:true name:My Waystone");
        assertEquals("abc123", a.hash());
        assertEquals("My Waystone", a.newName().orElse(null));
        assertTrue(a.hideName().orElse(false));
        assertEquals(AccessMode.GLOBAL, a.accessMode().orElse(null));
    }

    // --- the colon-in-name fix ---

    @Test
    void nameMayContainColonsAndSpaces() {
        // "Base: north" would previously truncate at the ": " — now name is greedy to end of string.
        ApplyArgs a = ApplyArgs.parse("h1 access:private hidename:- name:Base: north");
        assertEquals("Base: north", a.newName().orElse(null));
        assertEquals(AccessMode.PRIVATE, a.accessMode().orElse(null));
    }

    @Test
    void nameCanEvenLookLikeKeyTokens() {
        // free text after name: is verbatim, even if it contains "global:" etc.
        ApplyArgs a = ApplyArgs.parse("h1 access:team name:global:true server:yes");
        assertEquals("global:true server:yes", a.newName().orElse(null));
        assertEquals(AccessMode.TEAM, a.accessMode().orElse(null));
    }

    @Test
    void emptyNameValueIsPreserved() {
        ApplyArgs a = ApplyArgs.parse("h1 hidename:false name:");
        assertEquals("", a.newName().orElse("MISSING")); // present but empty
    }

    // --- sentinel / missing handling ---

    @Test
    void dashSentinelMeansLeaveUnchanged() {
        ApplyArgs a = ApplyArgs.parse("h1 access:- hidename:- name:-");
        assertTrue(a.newName().isEmpty(), "name:- => leave unchanged");
        assertTrue(a.hideName().isEmpty(), "hidename:- => leave unchanged");
        assertTrue(a.accessMode().isEmpty(), "access:- => no mode applied");
    }

    @Test
    void missingTokensAreEmpty() {
        ApplyArgs a = ApplyArgs.parse("justhash");
        assertEquals("justhash", a.hash());
        assertTrue(a.newName().isEmpty());
        assertTrue(a.hideName().isEmpty());
        assertTrue(a.accessMode().isEmpty());
        assertTrue(a.global().isEmpty());
    }

    @Test
    void hidenameParsesTrueFalse() {
        assertTrue(ApplyArgs.parse("h hidename:true").hideName().orElse(false));
        assertFalse(ApplyArgs.parse("h hidename:false").hideName().orElse(true));
        assertTrue(ApplyArgs.parse("h hidename:on").hideName().orElse(false));
    }

    // --- access: precedence over legacy per-field tokens ---

    @Test
    void accessWinsAndSuppressesLegacyFields() {
        ApplyArgs a = ApplyArgs.parse("h1 global:true team:true server:true access:private name:x");
        assertEquals(AccessMode.PRIVATE, a.accessMode().orElse(null));
        // legacy fields are IGNORED when access: is present — no apply-then-override
        assertTrue(a.global().isEmpty(), "global suppressed by access:");
        assertTrue(a.team().isEmpty(), "team suppressed by access:");
        assertTrue(a.server().isEmpty(), "server suppressed by access:");
    }

    @Test
    void legacyFieldsParseWhenNoAccessGiven() {
        ApplyArgs a = ApplyArgs.parse("h1 global:true team:false server:true hidename:true name:n");
        assertTrue(a.accessMode().isEmpty());
        assertTrue(a.global().orElse(false));
        assertFalse(a.team().orElse(true));
        assertTrue(a.server().orElse(false));
        assertTrue(a.hideName().orElse(false));
    }

    @Test
    void legacyDashSentinelLeavesFieldUnchanged() {
        ApplyArgs a = ApplyArgs.parse("h1 global:- team:true server:-");
        assertTrue(a.global().isEmpty());
        assertTrue(a.team().orElse(false));
        assertTrue(a.server().isEmpty());
    }

    @Test
    void whitespaceIsTolerated() {
        // the whole tail is trimmed once up front, so surrounding whitespace is stripped; interior
        // single spaces in the name are preserved verbatim.
        ApplyArgs a = ApplyArgs.parse("   h1    access:global    hidename:true    name:Trimmed Name  ");
        assertEquals("h1", a.hash());
        assertEquals(AccessMode.GLOBAL, a.accessMode().orElse(null));
        assertEquals("Trimmed Name", a.newName().orElse(null));
    }
}
