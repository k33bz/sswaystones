/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

// Forgetting a waystone must actually revoke it. Duplicates in the discovered list used to keep it usable
class PlayerDataTest {
    @Test
    void discoveringTwiceListsOnce() {
        PlayerData data = new PlayerData();
        data.discover("A");
        data.discover("A");
        assertEquals(List.of("A"), data.getDiscoveredWaystones());
    }

    @Test
    void forgetRevokesAfterARepeatedDiscover() {
        // Placing a waystone discovers it on creation and again on the first right-click
        PlayerData data = new PlayerData();
        data.discover("A");
        data.discover("A");
        data.forget("A");
        assertFalse(data.getDiscoveredWaystones().contains("A"));
    }

    @Test
    void oldSavesWithDuplicatesAreCleanedOnLoad() {
        PlayerData data = new PlayerData(List.of("A", "B", "A", "C", "B"));
        assertEquals(List.of("A", "B", "C"), data.getDiscoveredWaystones());
        data.forget("A");
        assertFalse(data.getDiscoveredWaystones().contains("A"));
    }

    @Test
    void forgetLeavesOtherWaystonesAlone() {
        PlayerData data = new PlayerData(List.of("A", "B"));
        data.forget("A");
        assertEquals(List.of("B"), data.getDiscoveredWaystones());
    }
}
