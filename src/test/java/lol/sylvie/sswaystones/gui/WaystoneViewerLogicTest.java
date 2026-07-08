/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic guards for the viewer GUI: page math, arrow visibility, and forget-eligibility.
 */
class WaystoneViewerLogicTest {

    // --- pagination math (maxPages / ceilDiv) ---

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

    // --- arrow visibility: ONLY when maxPages > 1 ---

    @Test
    void arrowsHiddenOnSinglePage() {
        assertFalse(WaystoneViewerLogic.showPageArrows(1));
    }

    @Test
    void arrowsShownWithMultiplePages() {
        assertTrue(WaystoneViewerLogic.showPageArrows(2));
        assertTrue(WaystoneViewerLogic.showPageArrows(9));
    }

    // --- page wrap-around ---

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

    // --- forget-eligibility (canForget) vs the looser lore hint ---

    @Test
    void canForgetOnlyForDiscoveredNonGlobalNonOwnedTracked() {
        // the happy path: not global, not owned by viewer, still tracked -> forgettable
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
        // e.g. already removed from storage — the delete guard must refuse
        assertFalse(WaystoneViewerLogic.canForget(false, false, false));
    }

    @Test
    void forgetLoreIsShownEvenWhenStorageIsNotConsulted() {
        // the lore hint intentionally ignores the storage-tracked bit (too costly per lore line)
        assertTrue(WaystoneViewerLogic.showForgetLore(false, false));
        assertFalse(WaystoneViewerLogic.showForgetLore(true, false));
        assertFalse(WaystoneViewerLogic.showForgetLore(false, true));
    }
}
