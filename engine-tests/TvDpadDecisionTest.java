import br.com.lcmtech.contentguard.TvDpadDecision;

public final class TvDpadDecisionTest {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError("FAILED: " + label);
        passed++;
        System.out.println("PASS: " + label);
    }

    public static void main(String[] args) {
        check(TvDpadDecision.recentForbiddenFocus(true, 1000L, 1800L, 17, 17),
                "block recent forbidden tile when current window matches");
        check(!TvDpadDecision.recentForbiddenFocus(false, 1000L, 1800L, 17, 17),
                "allow permitted tile");
        check(!TvDpadDecision.recentForbiddenFocus(true, 1000L, 4000L, 17, 17),
                "expired focus cannot block unrelated selection");
        check(!TvDpadDecision.recentForbiddenFocus(true, 1000L, 1800L, 17, 18),
                "other TV window must never inherit block");
        check(!TvDpadDecision.recentForbiddenFocus(true, 1000L, 900L, 17, 17),
                "clock rollover cannot authorize block");
        check(!TvDpadDecision.recentForbiddenFocus(true, 0L, 1800L, 17, 17),
                "unknown focused timestamp cannot block");
        check(TvDpadDecision.recentForbiddenFocus(true, 1000L, 1800L, -1, 18),
                "unknown event window permits short-lived fallback");
        System.out.println("TvDpadDecision: " + passed + " tests passed.");
    }
}
