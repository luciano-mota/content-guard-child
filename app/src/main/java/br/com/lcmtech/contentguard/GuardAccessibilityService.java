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
