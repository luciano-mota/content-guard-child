package br.com.lcmtech.contentguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java policy for the anti-tamper guard: system screens where the child
 * could disable, force-stop, clear or uninstall ContentGuard.
 */
public final class TamperRules {
    /** Screen text that identifies ContentGuard (app, service and listener labels). */
    public static final String GUARD_NAME = "ContentGuard";
    /** A parental pause can never be longer than this, even if the clock is changed. */
    public static final long MAX_PAUSE_MS = 15L * 60L * 1000L;

    private static final Set<String> SYSTEM_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.android.settings",               // Android / Samsung / Xiaomi settings
                    "com.android.tv.settings",            // Google TV / Android TV settings
                    "com.google.android.tv.settings",
                    "com.android.packageinstaller",       // uninstall confirmation
                    "com.google.android.packageinstaller",
                    "com.android.permissioncontroller",   // special app access
                    "com.google.android.permissioncontroller",
                    "com.miui.securitycenter",            // Xiaomi app info / permissions
                    "com.samsung.android.lool",           // Samsung device care (force stop)
                    "com.samsung.android.sm.devicesecurity"
            )));

    private TamperRules() {}

    public static boolean isSystemSettingsPackage(String packageName) {
        return packageName != null && SYSTEM_PACKAGES.contains(packageName);
    }

    public static boolean mentionsGuard(CharSequence text) {
        return text != null && text.length() <= 500
                && RuleMatcher.containsPhrase(text.toString(), GUARD_NAME);
    }

    /**
     * A pause is active only while {@code now} lies inside the window it was
     * granted for. Moving the clock backwards cannot extend it past
     * {@link #MAX_PAUSE_MS}; moving it forwards only ends it sooner.
     */
    public static boolean isPauseActive(long pausedUntilMillis, long nowMillis) {
        long remaining = pausedUntilMillis - nowMillis;
        return remaining > 0 && remaining <= MAX_PAUSE_MS;
    }
}
