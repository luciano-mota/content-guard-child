package br.com.lcmtech.contentguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java policy for the Android apps monitored for in-app searches.
 * Do not use these checks to inspect feeds, private messages, or comments.
 */
public final class SocialSearchRules {
    private static final Set<String> PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.zhiliaoapp.musically", // TikTok (Brazil and global)
                    "com.ss.android.ugc.trill", // TikTok regional variant
                    "com.kwai.video"            // Kwai Brazil
            )));

    private SocialSearchRules() {}

    public static boolean supportsPackage(String packageName) {
        return packageName != null && PACKAGES.contains(packageName);
    }

    /**
     * A recognisable search input (resource id, hint or description).
     * Words such as "edit", "text" and "input" alone are deliberately ignored
     * because those also apply to comments, messages and captions.
     */
    public static boolean isSearchFieldLabel(String label) {
        if (label == null || label.trim().isEmpty()) return false;
        String value = RuleMatcher.normalize(label);
        String padded = " " + value + " ";
        for (String word : new String[] {
                "search", "pesquisar", "pesquisa", "buscar", "busca",
                "query", "keyword"
        }) {
            if (padded.contains(" " + word + " ")) return true;
        }
        // Resource IDs sometimes concatenate words: searchEditText, searchBar.
        return value.contains("searchbar") || value.contains("searchbox")
                || value.contains("searchfield") || value.contains("searchinput")
                || value.contains("searchtext") || value.contains("searchview")
                || value.contains("searchhint")
                || value.contains("searchheader");
    }
}
