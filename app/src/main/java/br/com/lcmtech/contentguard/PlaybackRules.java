package br.com.lcmtech.contentguard;

/**
 * Pure Java policy for the playback guard, so it can be unit-tested.
 * Every media player is checked (YouTube, YouTube Kids, streaming apps,
 * browsers playing video), except ContentGuard itself.
 */
public final class PlaybackRules {
    /** Media-session fields longer than this are not titles; ignore them. */
    private static final int MAX_FIELD_LENGTH = 500;

    private PlaybackRules() {}

    public static boolean supportsPackage(String packageName, String ownPackage) {
        return packageName != null && !packageName.isEmpty()
                && !packageName.equals(ownPackage);
    }

    /**
     * True when any published field (title, channel, description) contains a
     * blocked phrase. Fields are matched one by one, never concatenated, so two
     * unrelated fields cannot accidentally form a blocked phrase together.
     */
    public static boolean metadataMatches(Iterable<? extends CharSequence> fields,
                                          Iterable<String> blockedPhrases) {
        if (fields == null || blockedPhrases == null) return false;
        for (CharSequence field : fields) {
            if (field == null || field.length() == 0
                    || field.length() > MAX_FIELD_LENGTH) continue;
            String value = field.toString();
            for (String phrase : blockedPhrases)
                if (RuleMatcher.containsPhrase(value, phrase)) return true;
        }
        return false;
    }
}
