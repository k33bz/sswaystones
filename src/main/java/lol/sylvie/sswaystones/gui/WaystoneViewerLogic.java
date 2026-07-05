/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

/**
 * Pure, game-runtime-free decisions extracted from the viewer GUIs so they can be unit-tested
 * without bootstrapping Minecraft. Every method here is a plain function of primitives/booleans;
 * the GUIs call into these so a regression in the logic fails a fast unit test rather than only
 * surfacing in a live server.
 */
public final class WaystoneViewerLogic {
    public static final int ITEMS_PER_PAGE = 9 * 5;

    private WaystoneViewerLogic() {
    }

    /** Number of pages needed for {@code count} entries, at least 1 (an empty list still shows page 1). */
    public static int maxPages(int count) {
        return maxPages(count, ITEMS_PER_PAGE);
    }

    public static int maxPages(int count, int itemsPerPage) {
        if (itemsPerPage <= 0)
            throw new IllegalArgumentException("itemsPerPage must be > 0");
        return Math.max(Math.ceilDiv(Math.max(count, 0), itemsPerPage), 1);
    }

    /**
     * The page arrows are only shown when there's actually more than one page, so a single-page list
     * isn't cluttered with dead "Page 1 of 1" controls (PR #50 polish).
     */
    public static boolean showPageArrows(int maxPages) {
        return maxPages > 1;
    }

    /** Wrap-around previous-page index (mirrors JavaViewerGui.previousPage). */
    public static int previousPage(int pageIndex, int maxPages) {
        int p = pageIndex - 1;
        return p < 0 ? maxPages - 1 : p;
    }

    /** Wrap-around next-page index (mirrors JavaViewerGui.nextPage). */
    public static int nextPage(int pageIndex, int maxPages) {
        int p = pageIndex + 1;
        return p >= maxPages ? 0 : p;
    }

    /**
     * Whether an entry may be forgotten (right-click). Forgetting is only allowed for a discovered,
     * non-global, non-owned waystone that the storage still tracks — this is the guard the
     * JavaViewerGui right-click callback enforces before opening the confirm dialog, and the same
     * predicate the PR #50 lore uses to decide whether to show "Right-click: Forget".
     */
    public static boolean canForget(boolean effectivelyGlobal, boolean ownedByViewer, boolean trackedByStorage) {
        return !effectivelyGlobal && !ownedByViewer && trackedByStorage;
    }

    /**
     * The lore-only "Right-click: Forget" hint uses a looser condition than the actual delete guard
     * (it can't cheaply consult the storage map when building 45 lore lines), so it's exposed
     * separately to keep the two intentionally-distinct rules honest under test.
     */
    public static boolean showForgetLore(boolean effectivelyGlobal, boolean ownedByViewer) {
        return !effectivelyGlobal && !ownedByViewer;
    }

    // --- permission-gating: which access toggles appear in the settings UIs ---

    /** Global toggle is offered iff the player has the global-create permission. */
    public static boolean showGlobalToggle(boolean hasGlobalPerm) {
        return hasGlobalPerm;
    }

    /** Team toggle is offered iff the player is ON a team AND has the team-create permission. */
    public static boolean showTeamToggle(boolean onTeam, boolean hasTeamPerm) {
        return onTeam && hasTeamPerm;
    }

    /** Server-owned toggle is offered iff the player has the server-create (admin) permission. */
    public static boolean showServerToggle(boolean hasServerPerm) {
        return hasServerPerm;
    }

    /**
     * How many togglable settings the sgui AccessSettingsGui will render. When zero, the GUI shows
     * the BARRIER "no settings available" fallback instead.
     */
    public static int availableToggleCount(boolean hasGlobalPerm, boolean onTeam, boolean hasTeamPerm,
            boolean hasServerPerm) {
        int n = 0;
        if (showGlobalToggle(hasGlobalPerm))
            n++;
        if (showTeamToggle(onTeam, hasTeamPerm))
            n++;
        if (showServerToggle(hasServerPerm))
            n++;
        return n;
    }

    public static boolean showNoSettingsFallback(boolean hasGlobalPerm, boolean onTeam, boolean hasTeamPerm,
            boolean hasServerPerm) {
        return availableToggleCount(hasGlobalPerm, onTeam, hasTeamPerm, hasServerPerm) == 0;
    }
}
