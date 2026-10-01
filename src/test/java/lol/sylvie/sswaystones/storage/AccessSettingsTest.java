/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import lol.sylvie.sswaystones.storage.WaystoneRecord.AccessSettings;
import org.junit.jupiter.api.Test;

class AccessSettingsTest {

    @Test
    void mutatorsApplyAndReadBack() {
        AccessSettings a = new AccessSettings(false, false, "");
        assertFalse(a.isGlobal());
        assertFalse(a.isServerOwned());
        assertFalse(a.hasTeam());
        assertFalse(a.isNameHidden());

        a.setGlobal(true);
        a.setServerOwned(true);
        a.setTeam("red");
        a.setNameHidden(true);

        assertTrue(a.isGlobal());
        assertTrue(a.isServerOwned());
        assertTrue(a.hasTeam());
        assertEquals("red", a.getTeam());
        assertTrue(a.isNameHidden());
        assertTrue(a.isEffectivelyGlobal()); // global OR server
    }

    @Test
    void codecRoundTripPreservesEveryField() {
        AccessSettings original = new AccessSettings(true, false, "blue", true);

        JsonElement json = AccessSettings.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        AccessSettings decoded = AccessSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(original.isGlobal(), decoded.isGlobal());
        assertEquals(original.isServerOwned(), decoded.isServerOwned());
        assertEquals(original.getTeam(), decoded.getTeam());
        assertEquals(original.isNameHidden(), decoded.isNameHidden());
        // and the serialized form actually carries hide_name
        assertTrue(json.getAsJsonObject().has("hide_name"));
        assertTrue(json.getAsJsonObject().get("hide_name").getAsBoolean());
    }

    @Test
    void legacySaveWithoutHideNameDefaultsToFalse() {
        // Saves from before hide_name have no such key
        JsonObject legacy = new JsonObject();
        legacy.addProperty("global", false);
        legacy.addProperty("server", true);
        legacy.addProperty("team", "");

        AccessSettings decoded = AccessSettings.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertTrue(decoded.isServerOwned());
        assertFalse(decoded.isNameHidden()); // optional field defaulted, no crash
    }

    @Test
    void hideNameToggleIsIndependentOfAccessFlags() {
        // Hiding the name must not affect who can access the waystone.
        AccessSettings a = new AccessSettings(false, false, "");
        a.setNameHidden(true);
        assertFalse(a.isEffectivelyGlobal());
        assertFalse(a.hasTeam());
        assertTrue(a.isNameHidden());
    }
}
