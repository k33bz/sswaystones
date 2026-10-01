/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

// Viewer page and forget rules, kept out of JavaViewerGui so they can be unit tested
public final class WaystoneViewerLogic {
    public static final int ITEMS_PER_PAGE = 9 * 5;

    private WaystoneViewerLogic() {
    }

    public static int maxPages(int count) {
        return Math.max(Math.ceilDiv(Math.max(count, 0), ITEMS_PER_PAGE), 1);
    }

    public static int previousPage(int pageIndex, int maxPages) {
        int p = pageIndex - 1;
        return p < 0 ? maxPages - 1 : p;
    }

    public static int nextPage(int pageIndex, int maxPages) {
        int p = pageIndex + 1;
        return p >= maxPages ? 0 : p;
    }

    public static boolean canForget(boolean effectivelyGlobal, boolean ownedByViewer, boolean trackedByStorage) {
        return !effectivelyGlobal && !ownedByViewer && trackedByStorage;
    }

    // The lore hint skips the storage lookup
    public static boolean showForgetLore(boolean effectivelyGlobal, boolean ownedByViewer) {
        return !effectivelyGlobal && !ownedByViewer;
    }
}
