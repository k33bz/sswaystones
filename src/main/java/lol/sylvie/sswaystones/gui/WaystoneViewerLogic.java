/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

/**
 * Viewer-GUI decisions extracted as pure functions so they can be unit-tested
 * without bootstrapping Minecraft.
 */
public final class WaystoneViewerLogic {
    public static final int ITEMS_PER_PAGE = 9 * 5;

    private WaystoneViewerLogic() {
    }

    /**
     * Number of pages for {@code count} entries, at least 1 (an empty list still
     * shows page 1).
     */
    public static int maxPages(int count) {
        return maxPages(count, ITEMS_PER_PAGE);
    }

    public static int maxPages(int count, int itemsPerPage) {
        if (itemsPerPage <= 0)
            throw new IllegalArgumentException("itemsPerPage must be > 0");
        return Math.max(Math.ceilDiv(Math.max(count, 0), itemsPerPage), 1);
    }

    /** Page arrows are only rendered when there is more than one page. */
    public static boolean showPageArrows(int maxPages) {
        return maxPages > 1;
    }

    /** Wrap-around previous-page index. */
    public static int previousPage(int pageIndex, int maxPages) {
        int p = pageIndex - 1;
        return p < 0 ? maxPages - 1 : p;
    }

    /** Wrap-around next-page index. */
    public static int nextPage(int pageIndex, int maxPages) {
        int p = pageIndex + 1;
        return p >= maxPages ? 0 : p;
    }

    /**
     * Whether an entry may be forgotten (right-click): non-global, not the viewer's
     * own, and still tracked by storage.
     */
    public static boolean canForget(boolean effectivelyGlobal, boolean ownedByViewer, boolean trackedByStorage) {
        return !effectivelyGlobal && !ownedByViewer && trackedByStorage;
    }

    /**
     * The "Right-click: Forget" lore hint uses a looser rule than
     * {@link #canForget} — it can't cheaply consult the storage map while building
     * 45 lore lines.
     */
    public static boolean showForgetLore(boolean effectivelyGlobal, boolean ownedByViewer) {
        return !effectivelyGlobal && !ownedByViewer;
    }
}
