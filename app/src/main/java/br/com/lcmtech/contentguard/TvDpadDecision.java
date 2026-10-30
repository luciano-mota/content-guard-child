package br.com.lcmtech.contentguard;

/** Small, pure-Java decision function for TV focus and select fallback. */
public final class TvDpadDecision {
    private static final long FOCUS_TTL_MS = 2500L;
    private TvDpadDecision() {}

    /**
     * Focus-based fallback is only applicable to a recent forbidden focused item
     * in the same YouTube window. Never use it to block another app's keypresses.
     */
    public static boolean recentForbiddenFocus(boolean blocked, long focusTime,
                                                 long now, int focusWindowId,
                                                 int currentWindowId) {
        if (!blocked || focusTime <= 0 || now < focusTime
                || now - focusTime > FOCUS_TTL_MS) return false;
        return focusWindowId < 0 || currentWindowId < 0
                || focusWindowId == currentWindowId;
    }
}
