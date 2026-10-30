package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * YouTube / YouTube Kids for TV, plus the anti-tamper check on TV settings.
 * Best-effort: service cannot inspect video streams, Google accounts, HTTPS
 * payloads, or guarantee exposure of player titles. Playback of blocked
 * videos is mainly stopped by {@link PlaybackGuardService}.
 */
public final class TvYoutubeAccessibilityService extends AccessibilityService {
    private static final Set<String> YOUTUBE_TV_APPS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.google.android.youtube.tv", "com.google.android.youtube.tvkids")));
    // Both TV activity and AccessibilityService run in the same app process.
    // This reflects onServiceConnected rather than just the Settings toggle.
    private static volatile boolean connected;
    private static volatile long lastYoutubeEventElapsed;

    public static boolean isConnected() { return connected; }
    /** Local-only timestamp, no title, text, video ID or search history retained. */
    public static long lastYoutubeEventElapsed() { return lastYoutubeEventElapsed; }
    private List<Rule> rules = Collections.emptyList();
    private long lastBlock;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        GuardControl.attach(this);
        rules = new RuleRepository(this).getAll();
        prefs = getSharedPreferences(RuleRepository.PREFS, MODE_PRIVATE);
        changeListener = (p, key) -> rules = new RuleRepository(this).getAll();
        prefs.registerOnSharedPreferenceChangeListener(changeListener);
        connected = true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        // Settings screens that could disable or uninstall the guard.
        if (GuardControl.blockTamper(this, event)) return;
        if (!YOUTUBE_TV_APPS.contains(event.getPackageName().toString())) return;
        // Received a real event from YouTube TV. We retain only a timestamp in
        // memory to help the parent diagnose whether the TV exposes any events.
        lastYoutubeEventElapsed = SystemClock.elapsedRealtime();
        if (rules.isEmpty() || SystemClock.elapsedRealtime() - lastBlock < 1800) return;
        int type = event.getEventType();
        AccessibilityNodeInfo source = event.getSource();
        try {
            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    && source != null && source.isEditable() && !source.isPassword()) {
                if (source.getText() != null && matches(source.getText().toString())) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    reject("Pesquisa bloqueada");
                }
            } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (clickedVideoMatches(event, source)) reject("Vídeo bloqueado");
            } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Only detect text on an explicitly identifiable active player
                // title widget. We must not block based on recommendations.
                if (playerTitleMatches()) reject("Reprodução bloqueada");
            }
        } finally {
            if (source != null) source.recycle();
        }
    }

    private boolean matches(String value) {
        for (Rule rule : rules)
            if (rule.type == Rule.Type.KEYWORD
                    && RuleMatcher.containsPhrase(value, rule.value)) return true;
        return false;
    }

    private boolean clickedVideoMatches(AccessibilityEvent event,
                                         AccessibilityNodeInfo source) {
        if (shortMatch(event.getContentDescription())) return true;
        for (CharSequence label : event.getText()) if (shortMatch(label)) return true;
        if (source == null) return false;
        if (smallSubtreeMatch(source, 3, new int[] {26})) return true;

        AccessibilityNodeInfo ancestor = source.getParent();
        for (int i = 0; i < 2 && ancestor != null; i++) {
            AccessibilityNodeInfo next = null;
            try {
                if (ancestor.isClickable() && ancestor.getChildCount() <= 5
                        && smallSubtreeMatch(ancestor, 2, new int[] {18})) return true;
                if (ancestor.getChildCount() <= 5) next = ancestor.getParent();
            } finally {
                ancestor.recycle();
            }
            ancestor = next;
        }
        if (ancestor != null) ancestor.recycle();
        return false;
    }

    private boolean shortMatch(CharSequence value) {
        return value != null && value.length() <= 250 && matches(value.toString());
    }

    private boolean smallSubtreeMatch(AccessibilityNodeInfo node,
                                      int depth, int[] remaining) {
        if (node == null || remaining[0]-- <= 0) return false;
        if (shortMatch(node.getText()) || shortMatch(node.getContentDescription())) return true;
        if (depth == 0 || node.getChildCount() > 7) return false;
        for (int i = 0; i < node.getChildCount() && remaining[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (smallSubtreeMatch(child, depth - 1, remaining)) return true;
            } finally { child.recycle(); }
        }
        return false;
    }

    private boolean playerTitleMatches() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            return GuardControl.playerTitleMatches(root, this::shortMatch, 0, new int[] {130});
        } finally { root.recycle(); }
    }

    private void reject(String reason) {
        lastBlock = SystemClock.elapsedRealtime();
        GuardControl.leaveScreen(false);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        GuardControl.detach(this);
        connected = false;
        lastYoutubeEventElapsed = 0L;
        if (prefs != null && changeListener != null)
            prefs.unregisterOnSharedPreferenceChangeListener(changeListener);
        super.onDestroy();
    }
}
