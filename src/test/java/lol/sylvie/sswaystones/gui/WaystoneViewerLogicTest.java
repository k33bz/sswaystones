/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaystoneViewerLogicTest {

    // Page count

    @Test
    void emptyListIsStillOnePage() {
        assertEquals(1, WaystoneViewerLogic.maxPages(0));
    }

    @Test
    void exactlyOneFullPageIsOnePage() {
        assertEquals(1, WaystoneViewerLogic.maxPages(WaystoneViewerLogic.ITEMS_PER_PAGE)); // 45 -> 1
    }

    @Test
    void oneOverAFullPageIsTwoPages() {
        assertEquals(2, WaystoneViewerLogic.maxPages(WaystoneViewerLogic.ITEMS_PER_PAGE + 1)); // 46 -> 2
    }

    @Test
    void ceilDivRoundsUp() {
        assertEquals(3, WaystoneViewerLogic.maxPages(91)); // ceil(91/45) = 3
        assertEquals(2, WaystoneViewerLogic.maxPages(90)); // exactly 2
    }

    // Wrap-around

    @Test
    void nextPageWrapsToZeroAtEnd() {
        assertEquals(1, WaystoneViewerLogic.nextPage(0, 3));
        assertEquals(0, WaystoneViewerLogic.nextPage(2, 3)); // wrap
    }

    @Test
    void previousPageWrapsToLastAtStart() {
        assertEquals(2, WaystoneViewerLogic.previousPage(0, 3)); // wrap
        assertEquals(0, WaystoneViewerLogic.previousPage(1, 3));
    }

    // Forgetting

    @Test
    void canForgetOnlyForDiscoveredNonGlobalNonOwnedTracked() {
        // not global, not the viewer's own, still in storage
        assertTrue(WaystoneViewerLogic.canForget(false, false, true));
    }

    @Test
    void cannotForgetGlobalWaystone() {
        assertFalse(WaystoneViewerLogic.canForget(true, false, true));
    }

    @Test
    void cannotForgetOwnWaystone() {
        assertFalse(WaystoneViewerLogic.canForget(false, true, true));
    }

    @Test
    void cannotForgetUntrackedWaystone() {
        // already removed from storage
        assertFalse(WaystoneViewerLogic.canForget(false, false, false));
    }

    @Test
    void forgetLoreIsShownEvenWhenStorageIsNotConsulted() {
        // the lore hint skips the storage lookup
        assertTrue(WaystoneViewerLogic.showForgetLore(false, false));
        assertFalse(WaystoneViewerLogic.showForgetLore(true, false));
        assertFalse(WaystoneViewerLogic.showForgetLore(false, true));
    }
}
