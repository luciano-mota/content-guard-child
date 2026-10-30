package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
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
    /** YouTube apps: also checked for the title of the video in the player. */
    private static final Set<String> YOUTUBE_APPS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "com.google.android.youtube", "com.google.android.apps.youtube.kids",
                    "app.revanced.android.youtube")));
    private static final Set<String> SUPPORTED_CONTENT_APPS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "com.google.android.youtube", "com.google.android.apps.youtube.kids",
                    "app.revanced.android.youtube",
                    // Google search bar/widget and Google Go: a search there skips the browser.
                    "com.google.android.googlequicksearchbox", "com.google.android.apps.searchlite",
                    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
                    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus",
                    "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
                    "com.microsoft.emmx", "com.brave.browser", "com.opera.browser",
                    "com.opera.mini.native", "com.opera.gx", "com.duckduckgo.mobile.android",
                    "com.mi.globalbrowser", "com.android.browser", "com.vivaldi.browser",
                    "com.kiwibrowser.browser", "com.UCMobile.intl")));
    private List<Rule> rules = Collections.emptyList();
    private long lastBlock = 0L;
    private long lastFeedScan = 0L;
    private long lastFeedSkip = 0L;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        GuardControl.attach(this);
        rules = new RuleRepository(this).getAll();
        preferences = getSharedPreferences(RuleRepository.PREFS, MODE_PRIVATE);
        listener = (p, key) -> rules = new RuleRepository(this).getAll();
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // Before any rule check: disabling/uninstalling must stay blocked even with no rules.
        if (GuardControl.blockTamper(this, event)) return;
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

        // TikTok and Kwai: search fields/selected results, then the visible
        // feed video (caption, hashtags, profile name).
        if (SocialSearchRules.supportsPackage(pkg)) {
            handleSocialSearchEvent(event);
            if (lastBlock < now) handleSocialFeedEvent(event, now);
            return;
        }

        if (!SUPPORTED_CONTENT_APPS.contains(pkg)) return;
        // Chrome's omnibox may report the focused URL field through window-content
        // events rather than a directly editable TYPE_VIEW_TEXT_CHANGED source.
        // Suggestions/history and voice search can also fill a field without a
        // text-change event, so every supported app gets its editable fields
        // (only editable ones) inspected when the window changes.
        final int type = event.getEventType();
        if ((type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                && blockSearchFieldIfMatched()) return;

        // Videos opened from the feed, autoplay or Shorts never pass through a
        // search: check the title of the video currently in the player.
        if (YOUTUBE_APPS.contains(pkg)
                && (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                && playerTitleMatches()) {
            block("Vídeo bloqueado", false);
            return;
        }

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
     * Search coverage for TikTok and Kwai.
     *
     * Accessibility exposure differs by app/version: this is best effort.
     */
    private void handleSocialSearchEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo source = event.getSource();
            try {
                if (source != null && socialSearchField(source)
                        && source.getText() != null
                        && matchesKeyword(source.getText().toString())) {
                    clearEditable(source);
                    block("Pesquisa bloqueada", false);
                    return;
                }
            } finally {
                if (source != null) source.recycle();
            }
        }

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                if (blockSocialSearchFieldInTree(root, 0, new int[] {150})) return;
            } finally {
                root.recycle();
            }
        }

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            try {
                // Only selected suggestions/results in a recognisable search UI.
                // A normal feed card can mention forbidden words but is not a search.
                if (!hasSocialSearchField(root, 0, new int[] {140})) return;
                AccessibilityNodeInfo source = event.getSource();
                try {
                    if (socialClickedItemMatches(event, source))
                        block("Pesquisa bloqueada", false);
                } finally {
                    if (source != null) source.recycle();
                }
            } finally {
                root.recycle();
            }
        }
    }

    /**
     * TikTok/Kwai do not publish the playing video to the media session, so
     * the playback guard cannot see it. Check the text visible on screen
     * (caption, hashtags, profile name): first swipe to the next video; if
     * blocked text is still on screen right after, leave the screen.
     */
    private void handleSocialFeedEvent(AccessibilityEvent event, long now) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (now - lastFeedScan < SocialSearchRules.FEED_SCAN_INTERVAL_MS) return;
        lastFeedScan = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!visibleTextMatches(root, 0, new int[] {300})) return;
        } finally {
            root.recycle();
        }
        if (SocialSearchRules.shouldLeaveFeed(lastFeedSkip, now)) {
            lastFeedSkip = 0L;
            block("Vídeo bloqueado", false);
            return;
        }
        lastFeedSkip = now;
        lastBlock = now;
        if (!swipeToNextVideo()) GuardControl.leaveScreen(false);
        Toast.makeText(this, "Vídeo bloqueado", Toast.LENGTH_SHORT).show();
    }

    /** Only text the child can see; editable fields are handled as searches. */
    private boolean visibleTextMatches(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        if (node.isVisibleToUser() && !node.isEditable()) {
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            if ((text != null && text.length() <= 300 && matchesKeyword(text.toString()))
                    || (description != null && description.length() <= 300
                        && matchesKeyword(description.toString()))) return true;
        }
        if (depth >= 30) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (visibleTextMatches(child, depth + 1, budget)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    /** Same gesture the child uses to go to the next video. */
    private boolean swipeToNextVideo() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float x = metrics.widthPixels / 2f;
        Path path = new Path();
        path.moveTo(x, metrics.heightPixels * 0.75f);
        path.lineTo(x, metrics.heightPixels * 0.25f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 200L))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private void clearEditable(AccessibilityNodeInfo node) {
        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private boolean socialSearchField(AccessibilityNodeInfo node) {
        if (node == null || !node.isEditable() || node.isPassword()) return false;
        if (searchNodeLabel(node)) return true;
        AccessibilityNodeInfo parent = node.getParent();
        for (int depth = 0; depth < 2 && parent != null; depth++) {
            AccessibilityNodeInfo next = null;
            try {
                if (searchNodeLabel(parent)) return true;
                next = parent.getParent();
            } finally {
                parent.recycle();
            }
            parent = next;
        }
        return false;
    }

    private boolean searchNodeLabel(AccessibilityNodeInfo node) {
        CharSequence hint = node.getHintText();
        CharSequence description = node.getContentDescription();
        return SocialSearchRules.isSearchFieldLabel(node.getViewIdResourceName())
                || (hint != null && SocialSearchRules.isSearchFieldLabel(hint.toString()))
                || (description != null
                    && SocialSearchRules.isSearchFieldLabel(description.toString()));
    }

    private boolean blockSocialSearchFieldInTree(AccessibilityNodeInfo node,
                                                  int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        if (socialSearchField(node) && node.getText() != null
                && matchesKeyword(node.getText().toString())) {
            clearEditable(node);
            block("Pesquisa bloqueada", false);
            return true;
        }
        if (depth >= 11) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (blockSocialSearchFieldInTree(child, depth + 1, budget))
                    return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private boolean hasSocialSearchField(AccessibilityNodeInfo node,
                                          int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        if (socialSearchField(node)) return true;
        if (depth >= 11) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (hasSocialSearchField(child, depth + 1, budget))
                    return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private boolean socialClickedItemMatches(AccessibilityEvent event,
                                              AccessibilityNodeInfo source) {
        CharSequence description = event.getContentDescription();
        if (description != null && matchesKeyword(description.toString())) return true;
        for (CharSequence text : event.getText())
            if (text != null && matchesKeyword(text.toString())) return true;
        if (source == null) return false;
        // Limit the scan to the touched row; never scan the entire results list.
        if (socialSmallSubtreeMatches(source, 2, new int[] {20})) return true;
        AccessibilityNodeInfo parent = source.getParent();
        if (parent == null) return false;
        try {
            return parent.isClickable() && parent.getChildCount() <= 5
                    && socialSmallSubtreeMatches(parent, 2, new int[] {18});
        } finally {
            parent.recycle();
        }
    }

    private boolean socialSmallSubtreeMatches(AccessibilityNodeInfo node,
                                               int depth, int[] budget) {
        if (node == null || budget[0]-- <= 0) return false;
        CharSequence value = node.getText();
        CharSequence description = node.getContentDescription();
        if ((value != null && matchesKeyword(value.toString()))
                || (description != null && matchesKeyword(description.toString())))
            return true;
        if (depth == 0 || node.getChildCount() > 6) return false;
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                if (socialSmallSubtreeMatches(child, depth - 1, budget))
                    return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    /**
     * Chrome often sends content/window events for a selected history suggestion,
     * not TEXT_CHANGED. Scan a bounded number of accessibility nodes, but only
     * read editable inputs: NEVER match against arbitrary Google result cards.
     */
    private boolean blockSearchFieldIfMatched() {
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
        GuardControl.leaveScreen(returnHome);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    @Override public void onInterrupt() {}

    private boolean playerTitleMatches() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            return GuardControl.playerTitleMatches(root, value -> value != null
                    && value.length() <= 250 && matchesKeyword(value.toString()),
                    0, new int[] {160});
        } finally {
            root.recycle();
        }
    }

    @Override public void onDestroy() {
        GuardControl.detach(this);
        if (preferences != null && listener != null)
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
