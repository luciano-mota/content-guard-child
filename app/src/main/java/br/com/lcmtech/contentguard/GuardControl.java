package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Shared by the phone and TV accessibility services: leaving a blocked
 * screen, the PIN-authorised settings pause and the anti-tamper check.
 * Stores only a pause deadline; never stores screen text.
 */
public final class GuardControl {
    private static final String PREFS = "content_guard_control";
    private static final String KEY_PAUSED_UNTIL = "settings_paused_until";
    /** One BACK per interval, so two guards reacting together cannot close the app. */
    private static final long ACTION_INTERVAL_MS = 1500L;
    private static volatile AccessibilityService active;
    private static volatile long lastAction;

    private GuardControl() {}

    static void attach(AccessibilityService service) { active = service; }

    static void detach(AccessibilityService service) {
        if (active == service) active = null;
    }

    /** Performs BACK (or HOME) through the connected accessibility service. */
    static boolean leaveScreen(boolean home) {
        AccessibilityService service = active;
        if (service == null) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAction < ACTION_INTERVAL_MS) return false;
        lastAction = now;
        return service.performGlobalAction(home
                ? AccessibilityService.GLOBAL_ACTION_HOME
                : AccessibilityService.GLOBAL_ACTION_BACK);
    }

    /**
     * Leaves the player only when {@code packageName} is on screen, so a blocked
     * song playing in the background never presses BACK in another app.
     */
    static void leavePlayerIfForeground(String packageName) {
        AccessibilityService service = active;
        if (service == null || packageName == null) return;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;
        try {
            CharSequence foreground = root.getPackageName();
            if (foreground != null && packageName.contentEquals(foreground))
                leaveScreen(false);
        } finally {
            root.recycle();
        }
    }

    // ---- Parental pause: lets the adult open ContentGuard's system settings ----

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Only called from a PIN-authenticated screen. */
    public static void pauseSettingsGuard(Context context) {
        prefs(context).edit().putLong(KEY_PAUSED_UNTIL,
                System.currentTimeMillis() + TamperRules.MAX_PAUSE_MS).apply();
    }

    public static void resumeSettingsGuard(Context context) {
        prefs(context).edit().remove(KEY_PAUSED_UNTIL).apply();
    }

    public static long settingsPauseRemainingMs(Context context) {
        long until = prefs(context).getLong(KEY_PAUSED_UNTIL, 0L);
        long now = System.currentTimeMillis();
        return TamperRules.isPauseActive(until, now) ? until - now : 0L;
    }

    /**
     * Anti-tamper: when a system screen that names ContentGuard opens (app
     * info, accessibility, notification access, uninstall dialog), go HOME.
     * Without it, the child could simply disable or uninstall the guard.
     * Returns true when the event was a guarded system screen.
     */
    static boolean blockTamper(AccessibilityService service, AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !TamperRules.isSystemSettingsPackage(pkg.toString())) return false;
        if (settingsPauseRemainingMs(service) > 0) return true;

        boolean guarded = TamperRules.mentionsGuard(event.getContentDescription());
        for (CharSequence text : event.getText())
            guarded = guarded || TamperRules.mentionsGuard(text);
        if (!guarded) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) return true;
            try {
                guarded = treeMatches(root, TamperRules::mentionsGuard, 0, new int[] {400});
            } finally {
                root.recycle();
            }
        }
        if (guarded && leaveScreen(true)) {
            Toast.makeText(service, "Protegido pelo ContentGuard. Peça à responsável.",
                    Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** Bounded scan of visible text and descriptions. */
    static boolean treeMatches(AccessibilityNodeInfo node, Predicate<CharSequence> test,
                               int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        if (test.test(node.getText()) || test.test(node.getContentDescription())) return true;
        if (depth >= 20) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (treeMatches(child, test, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    /**
     * Title of the video currently in the player (watch page, Shorts/reels).
     * Requires a view ID with both "title" and a player context; generic
     * titles in home/search lists are deliberately ignored.
     */
    static boolean playerTitleMatches(AccessibilityNodeInfo node, Predicate<CharSequence> test,
                                      int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        String id = node.getViewIdResourceName();
        if (id != null) {
            String token = id.toLowerCase(Locale.ROOT);
            boolean playerTitle = token.contains("title")
                    && (token.contains("player") || token.contains("watch")
                        || token.contains("reel") || token.contains("shorts"));
            if (playerTitle && (test.test(node.getText())
                    || test.test(node.getContentDescription()))) return true;
        }
        if (depth >= 14) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (playerTitleMatches(child, test, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }
}
