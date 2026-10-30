import br.com.lcmtech.contentguard.TamperRules;

public final class TamperRulesTest {
    private static int passed;
    private static void check(boolean expression, String name) {
        if (!expression) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) {
        check(TamperRules.isSystemSettingsPackage("com.android.settings"), "phone settings");
        check(TamperRules.isSystemSettingsPackage("com.android.tv.settings"), "Google TV settings");
        check(TamperRules.isSystemSettingsPackage("com.google.android.packageinstaller"),
                "uninstall dialog");
        check(!TamperRules.isSystemSettingsPackage("com.google.android.youtube"),
                "content apps are not settings");
        check(!TamperRules.isSystemSettingsPackage(null), "null package");
        check(TamperRules.mentionsGuard("ContentGuard Child"), "app label");
        check(TamperRules.mentionsGuard("ContentGuard · Filtro local"), "service label");
        check(TamperRules.mentionsGuard("Desinstalar ContentGuard Child TV?"), "uninstall prompt");
        check(!TamperRules.mentionsGuard("Wi-Fi"), "other settings allowed");
        check(!TamperRules.mentionsGuard(null), "null text");
        long now = 1_000_000_000L;
        check(TamperRules.isPauseActive(now + 60_000L, now), "pause running");
        check(!TamperRules.isPauseActive(now - 1L, now), "pause expired");
        check(!TamperRules.isPauseActive(0L, now), "never paused");
        check(!TamperRules.isPauseActive(now + TamperRules.MAX_PAUSE_MS + 1L, now),
                "clock moved back cannot extend pause");
        System.out.println("TamperRules: " + passed + " tests passed.");
    }
}
