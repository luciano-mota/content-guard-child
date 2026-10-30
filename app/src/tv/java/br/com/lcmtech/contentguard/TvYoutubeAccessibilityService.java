package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parental, YouTube-TV-only best-effort screen controls.
 *
 * First: intercept named searches where exposed by accessibility.
 * Second: consume the remote's OK/Enter on forbidden focused video titles.
 * Third: BACK after a video player exposes an explicitly labelled title.
 *
 * No internet permission, video decoding, OCR or stored browsing data.
 */
public final class TvYoutubeAccessibilityService extends AccessibilityService {
    private static final String YOUTUBE_TV = "com.google.android.youtube.tv";
    private static volatile boolean connected;
    private static volatile long lastYoutubeEventElapsed;
    private static volatile long lastRemoteKeyElapsed;
    private static volatile int blockedRemotePresses;

    public static boolean isConnected() { return connected; }
    public static long lastYoutubeEventElapsed() { return lastYoutubeEventElapsed; }
    public static long lastRemoteKeyElapsed() { return lastRemoteKeyElapsed; }
    public static int blockedRemotePresses() { return blockedRemotePresses; }

    private List<Rule> rules = Collections.emptyList();
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private long lastBlock;
    private long focusedAt;
    private int focusWindowId = -1;
    private boolean focusedForbidden;
    private int consumedKey = -1;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        rules = new RuleRepository(this).getAll();
        preferences = getSharedPreferences(RuleRepository.PREFS, MODE_PRIVATE);
        listener = (p, key) -> rules = new RuleRepository(this).getAll();
        preferences.registerOnSharedPreferenceChangeListener(listener);
        connected = true;
    }

    /** Can consume ENTER/OK before YouTube sees it when the active tile title is accessible. */
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int key = event.getKeyCode();
        if (!isSelectionKey(key)) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (consumedKey == key) {
                consumedKey = -1;
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0)
            return false;

        // Keyboard input is global to accessibility services. A hard check
        // against the foreground package is mandatory to avoid blocking TV UI.
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        boolean forbidden = false;
        try {
            if (!isYoutubeRoot(root)) {
                focusedForbidden = false;
                return false;
            }
            lastRemoteKeyElapsed = SystemClock.elapsedRealtime();
            if (rules.isEmpty()) return false;
            forbidden = activeFocusedVideoMatches(root);
            if (!forbidden) {
                forbidden = TvDpadDecision.recentForbiddenFocus(
                        focusedForbidden, focusedAt, SystemClock.elapsedRealtime(),
                        focusWindowId, root.getWindowId());
            }
        } finally {
            root.recycle();
        }
        if (!forbidden) return false;
        consumedKey = key;
        blockedRemotePresses++;
        focusedForbidden = false;
        focusedAt = 0;
        lastBlock = SystemClock.elapsedRealtime();
        Toast.makeText(this, "Vídeo bloqueado", Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean isSelectionKey(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !YOUTUBE_TV.contentEquals(event.getPackageName())) return;
        lastYoutubeEventElapsed = SystemClock.elapsedRealtime();
        if (rules.isEmpty()) return;

        int type = event.getEventType();
        AccessibilityNodeInfo source = event.getSource();
        try {
            if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                    || type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                    || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
                // Only cache the focused item's title (boolean), not text.
                // A new focus on a permitted item clears the old decision.
                focusedForbidden = source != null && focusedTileMatches(source);
                focusedAt = SystemClock.elapsedRealtime();
                focusWindowId = event.getWindowId();
                return;
            }

            if (SystemClock.elapsedRealtime() - lastBlock < 1600L) return;

            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (matchesSearchText(event, source)) {
                    if (source != null && source.isEditable()) clearEditable(source);
                    reject("Pesquisa bloqueada");
                }
            } else if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                // Fallback for remotes/services where key filtering is unavailable.
                if (clickedVideoMatches(event, source)) reject("Vídeo bloqueado");
            } else if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (searchFieldInActiveWindowMatches()) {
                    reject("Pesquisa bloqueada");
                } else if (playerTitleMatches()) {
                    // Only explicitly identified active player title widgets.
                    reject("Reprodução bloqueada");
                }
            }
        } finally {
            if (source != null) source.recycle();
        }
    }

    private boolean isYoutubeRoot(AccessibilityNodeInfo root) {
        return root.getPackageName() != null
                && YOUTUBE_TV.contentEquals(root.getPackageName());
    }

    private boolean matchesSearchText(AccessibilityEvent event, AccessibilityNodeInfo source) {
        if (source == null || source.isPassword()) return false;
        // Editable controls within YouTube TV are usually search; text events
        // from custom search widgets may have no editable flag.
        if (!source.isEditable() && !searchIdentity(source)) return false;
        if (source.getText() != null && matches(source.getText().toString())) return true;
        for (CharSequence item : event.getText()) {
            if (shortMatch(item)) return true;
        }
        return false;
    }

    private boolean searchIdentity(AccessibilityNodeInfo node) {
        if (node == null) return false;
        String id = node.getViewIdResourceName();
        if (id != null) {
            String s = id.toLowerCase(Locale.ROOT);
            if (s.contains("search") || s.contains("query") || s.contains("pesquisa"))
                return true;
        }
        CharSequence hint = node.getHintText();
        if (hint != null) {
            String normalized = RuleMatcher.normalize(hint.toString());
            if (normalized.contains("pesquis") || normalized.contains("buscar")
                    || normalized.contains("search")) return true;
        }
        return false;
    }

    private boolean searchFieldInActiveWindowMatches() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            if (!isYoutubeRoot(root)) return false;
            return findBlockedSearchControl(root, 0, new int[] {160});
        } finally {
            root.recycle();
        }
    }

    private boolean findBlockedSearchControl(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (budget[0]-- <= 0) return false;
        if ((node.isEditable() || searchIdentity(node)) && !node.isPassword()
                && shortMatch(node.getText())) {
            if (node.isEditable()) clearEditable(node);
            return true;
        }
        if (depth >= 12) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (findBlockedSearchControl(child, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private void clearEditable(AccessibilityNodeInfo node) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    /**
     * Read only the focused item and its small clickable tile ancestor.
     * Never search the entire visible list; that would falsely block a
     * permitted video when other recommendations contain forbidden terms.
     */
    private boolean activeFocusedVideoMatches(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) return false;
        try {
            return focusedTileMatches(focused);
        } finally {
            focused.recycle();
        }
    }

    private boolean focusedTileMatches(AccessibilityNodeInfo source) {
        if (smallSubtreeMatch(source, 3, new int[] {22})) return true;
        AccessibilityNodeInfo parent = source.getParent();
        for (int i = 0; i < 3 && parent != null; i++) {
            AccessibilityNodeInfo next = null;
            try {
                if (parent.getChildCount() <= 8 && (parent.isClickable() || parent.isFocusable())
                        && smallSubtreeMatch(parent, 3, new int[] {25})) return true;
                if (parent.getChildCount() <= 8) next = parent.getParent();
            } finally {
                parent.recycle();
            }
            parent = next;
        }
        if (parent != null) parent.recycle();
        return false;
    }

    private boolean clickedVideoMatches(AccessibilityEvent event, AccessibilityNodeInfo source) {
        if (shortMatch(event.getContentDescription())) return true;
        for (CharSequence value : event.getText()) if (shortMatch(value)) return true;
        return source != null && focusedTileMatches(source);
    }

    private boolean matches(String value) {
        for (Rule rule : rules)
            if (rule.type == Rule.Type.KEYWORD
                    && RuleMatcher.containsPhrase(value, rule.value)) return true;
        return false;
    }

    private boolean shortMatch(CharSequence value) {
        return value != null && value.length() <= 250 && matches(value.toString());
    }

    private boolean smallSubtreeMatch(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (budget[0]-- <= 0) return false;
        if (shortMatch(node.getText()) || shortMatch(node.getContentDescription())) return true;
        if (depth <= 0 || node.getChildCount() > 8) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (smallSubtreeMatch(child, depth - 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private boolean playerTitleMatches() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            return isYoutubeRoot(root) && checkPlayerTitle(root, 0, new int[] {140});
        } finally {
            root.recycle();
        }
    }

    private boolean checkPlayerTitle(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (budget[0]-- <= 0) return false;
        String id = node.getViewIdResourceName();
        if (id != null) {
            String token = id.toLowerCase(Locale.ROOT);
            if (token.contains("title") && (token.contains("player") || token.contains("watch"))
                    && (shortMatch(node.getText())
                        || shortMatch(node.getContentDescription()))) return true;
        }
        if (depth >= 12) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (checkPlayerTitle(child, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private void reject(String reason) {
        lastBlock = SystemClock.elapsedRealtime();
        focusedForbidden = false;
        focusedAt = 0;
        performGlobalAction(GLOBAL_ACTION_BACK);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        connected = false;
        lastYoutubeEventElapsed = 0;
        lastRemoteKeyElapsed = 0;
        blockedRemotePresses = 0;
        if (preferences != null && listener != null)
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
