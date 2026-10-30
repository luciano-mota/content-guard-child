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
        if ("com.android.chrome".equals(pkg)
                && (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                && blockChromeAddressIfMatched()) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        try {
            int type = event.getEventType();
            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && source.isEditable() && !source.isPassword()) {
                String text = source.getText() == null ? "" : source.getText().toString();
                if (matchesKeyword(text) || matchesDomain(text)) {
                    // Clear forbidden search/URL before leaving; no keypress logging.
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    block("Pesquisa bloqueada", false);
                }
            } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                // Read just the clicked control/subtree, not the whole results page.
                if (clickedTitleMatches(source)) block("Conteúdo bloqueado", false);
            }
        } finally {
            source.recycle();
        }
    }

    /**
     * Best-effort handling of Chrome omnibox. We deliberately do not scan the
     * whole web page or use arbitrary page text: that would block unrelated
     * content when a recommendation mentions a forbidden word.
     *
     * No accessibility API guarantees access to every Chrome version's URL bar.
     */
    private boolean blockChromeAddressIfMatched() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) return false;
            try {
                if (focused.isPassword() || !isChromeOmnibox(focused)) return false;
                CharSequence value = focused.getText();
                if (value == null || (!matchesKeyword(value.toString())
                        && !matchesDomain(value.toString()))) return false;
                Bundle clearArgs = new Bundle();
                clearArgs.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                // In some Chrome versions, the omnibox refuses ACTION_SET_TEXT.
                // We still back out of the address field instead of submitting it.
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);
                block("Pesquisa bloqueada", false);
                return true;
            } finally {
                focused.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private boolean isChromeOmnibox(AccessibilityNodeInfo focused) {
        AccessibilityNodeInfo node = focused;
        // The edit field may be wrapped in one or two Chrome UI containers.
        for (int depth = 0; depth < 3 && node != null; depth++) {
            String id = node.getViewIdResourceName();
            if (id != null && (id.endsWith(":id/url_bar")
                    || id.endsWith(":id/search_box_text")
                    || id.toLowerCase(java.util.Locale.ROOT).contains("omnibox"))) {
                if (node != focused) node.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = node.getParent();
            if (node != focused) node.recycle();
            node = parent;
        }
        if (node != null && node != focused) node.recycle();
        return false;
    }

    private boolean clickedTitleMatches(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence label = node.getText();
        CharSequence description = node.getContentDescription();
        if ((label != null && matchesKeyword(label.toString()))
                || (description != null && matchesKeyword(description.toString()))) return true;
        int count = Math.min(node.getChildCount(), 12);
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    CharSequence text = child.getText();
                    CharSequence desc = child.getContentDescription();
                    if ((text != null && matchesKeyword(text.toString()))
                            || (desc != null && matchesKeyword(desc.toString()))) return true;
                } finally { child.recycle(); }
            }
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
