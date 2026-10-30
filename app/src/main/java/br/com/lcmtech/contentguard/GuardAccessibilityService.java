package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import android.content.SharedPreferences;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * Best-effort local content controls. No OCR, interception of HTTPS content, or hidden monitoring.
 * It cannot guarantee removal of YouTube recommendations or prevent all content flashes.
 */
public final class GuardAccessibilityService extends AccessibilityService {
    private static final Set<String> SUPPORTED_CONTENT_APPS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "com.google.android.youtube", "com.android.chrome", "org.mozilla.firefox",
                    "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser")));
    private List<Rule> rules = Collections.emptyList();
    private long lastBlock = 0L;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        rules = new RuleRepository(this).getAll();
        preferences = getSharedPreferences(RuleRepository.PREFS, MODE_PRIVATE);
        listener = (p, key) -> rules = new RuleRepository(this).getAll();
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || rules.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBlock < 1500L) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            for (Rule rule : rules) {
                if (rule.type == Rule.Type.APP && pkg.equalsIgnoreCase(rule.value)) {
                    block("Aplicativo bloqueado", true);
                    return;
                }
            }
        }

        if (!SUPPORTED_CONTENT_APPS.contains(pkg)) return;
        // Chrome's omnibox may report the focused URL field through window-content
        // events rather than a directly editable TYPE_VIEW_TEXT_CHANGED source.
        // Only inspect Chrome's currently focused address/search field.
        final int type = event.getEventType();
        // Suggestions/history can navigate without producing a text-change event.
        // Inspect only editable Chrome fields when the active window changes,
        // including the Google results search box after selecting a suggestion.
        if ("com.android.chrome".equals(pkg)
                && (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                && blockChromeSearchFieldIfMatched()) return;

        AccessibilityNodeInfo source = event.getSource();
        try {
            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    && source != null && source.isEditable() && !source.isPassword()) {
                String value = source.getText() == null ? "" : source.getText().toString();
                if (matchesKeyword(value) || matchesDomain(value)
                        || matchesBlockedGoogleQueryUrl(value)) {
                    // Clear forbidden search/URL before leaving; no keypress logging.
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    block("Pesquisa bloqueada", false);
                }
            } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                    || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
                // Read only the activated suggestion/row, never all recommendations.
                if (clickedSuggestionMatches(event, source))
                    block("Conteúdo bloqueado", false);
            }
        } finally {
            if (source != null) source.recycle();
        }
    }

    /**
     * Chrome often sends content/window events for a selected history suggestion,
     * not TEXT_CHANGED. Scan a bounded number of accessibility nodes, but only
     * read editable inputs: NEVER match against arbitrary Google result cards.
     */
    private boolean blockChromeSearchFieldIfMatched() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            return findBlockedEditable(root, 0, new int[] {180});
        } finally {
            root.recycle();
        }
    }

    private boolean findBlockedEditable(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        if (node.isEditable() && !node.isPassword()) {
            CharSequence label = node.getText();
            String value = label == null ? "" : label.toString();
            if (matchesKeyword(value) || matchesDomain(value)
                    || matchesBlockedGoogleQueryUrl(value)) {
                Bundle clearArgs = new Bundle();
                clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);
                block("Pesquisa bloqueada", false);
                return true;
            }
        }
        if (depth >= 11) return false;
        final int childCount = node.getChildCount();
        for (int i = 0; i < childCount && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (findBlockedEditable(child, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    /**
     * For a tapped suggestion, Chrome/Google may report just an icon as the
     * event source; the suggestion text lives in its clickable parent row.
     * Limit ancestor/subtree scope so a blocked *different* suggestion in the
     * list cannot incorrectly block a permitted one.
     */
    private boolean clickedSuggestionMatches(AccessibilityEvent event,
                                              AccessibilityNodeInfo source) {
        CharSequence description = event.getContentDescription();
        if (description != null && description.length() <= 240
                && matchesKeywordOrSite(description.toString())) return true;
        for (CharSequence text : event.getText()) {
            if (text != null && text.length() <= 240
                    && matchesKeywordOrSite(text.toString())) return true;
        }
        if (source == null) return false;
        if (limitedSubtreeMatches(source, 3, new int[] {24})) return true;

        AccessibilityNodeInfo parent = source.getParent();
        for (int depth = 0; depth < 2 && parent != null; depth++) {
            AccessibilityNodeInfo next = null;
            try {
                // The nearest compact clickable ancestor typically represents
                // a single history/suggestion item. Do not scan large lists.
                if (parent.getChildCount() <= 6
                        && parent.isClickable()
                        && limitedSubtreeMatches(parent, 3, new int[] {24}))
                    return true;
                if (parent.getChildCount() <= 6) next = parent.getParent();
            } finally {
                parent.recycle();
            }
            parent = next;
        }
        if (parent != null) parent.recycle();
        return false;
    }

    private boolean limitedSubtreeMatches(AccessibilityNodeInfo node,
                                          int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if ((text != null && matchesKeywordOrSite(text.toString()))
                || (desc != null && matchesKeywordOrSite(desc.toString()))) return true;
        if (depth <= 0 || node.getChildCount() > 8) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (limitedSubtreeMatches(child, depth - 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private boolean matchesKeywordOrSite(String value) {
        return matchesKeyword(value) || matchesDomain(value)
                || matchesBlockedGoogleQueryUrl(value);
    }

    private boolean matchesBlockedGoogleQueryUrl(String value) {
        for (Rule rule : rules) {
            if (rule.type == Rule.Type.KEYWORD
                    && RuleMatcher.matchesGoogleSearchUrl(value, rule.value))
                return true;
        }
        return false;
    }

    private boolean matchesKeyword(String text) {
        for (Rule rule : rules)
            if (rule.type == Rule.Type.KEYWORD && RuleMatcher.containsPhrase(text, rule.value)) return true;
        return false;
    }
    private boolean matchesDomain(String text) {
        for (Rule rule : rules)
            if (rule.type == Rule.Type.DOMAIN && RuleMatcher.matchesDomain(text, rule.value)) return true;
        return false;
    }
    private void block(String reason, boolean returnHome) {
        lastBlock = SystemClock.elapsedRealtime();
        // Back keeps YouTube/browser open; Home is used only for a blocked app.
        performGlobalAction(returnHome ? GLOBAL_ACTION_HOME : GLOBAL_ACTION_BACK);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        if (preferences != null && listener != null)
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
