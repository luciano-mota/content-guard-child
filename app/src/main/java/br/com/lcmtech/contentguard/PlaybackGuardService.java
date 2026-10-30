package br.com.lcmtech.contentguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Playback guard for phone and Google TV.
 *
 * Players publish the video being played to the Android media session (the
 * data behind "Now playing" and the media notification). This works even
 * when YouTube for TV hides its titles from Accessibility, and covers videos
 * opened from the feed, autoplay, voice search or links, not only searches.
 *
 * Reading active media sessions requires notification-listener access; this
 * service never reads, stores or dismisses notifications. When the playing
 * title or channel contains a blocked phrase, playback is paused and, if the
 * player is on screen, the accessibility service leaves it.
 *
 * Best effort: the first frames may still appear before the pause.
 */
public final class PlaybackGuardService extends NotificationListenerService {
    /** Lets the player publish the new title before we check a state change. */
    private static final long CHECK_DELAY_MS = 300L;
    private static final long NOTICE_INTERVAL_MS = 3000L;
    private static volatile boolean connected;
    private static volatile long lastSessionEventElapsed;

    public static boolean isConnected() { return connected; }
    /** Local-only timestamp, no title, channel or video ID retained. */
    public static long lastSessionEventElapsed() { return lastSessionEventElapsed; }

    /** Whether the adult granted notification access to this service. */
    public static boolean isGranted(Context context) {
        try {
            String setting = Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            if (setting == null) return false;
            ComponentName target = new ComponentName(context, PlaybackGuardService.class);
            for (String entry : setting.split(":")) {
                if (target.equals(ComponentName.unflattenFromString(entry.trim()))) return true;
            }
        } catch (SecurityException ignored) {
            // Some firmware may restrict reading this setting.
        }
        return false;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> controllers = new HashMap<>();
    private final Map<MediaSession.Token, MediaController.Callback> callbacks = new HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            this::track;
    private final Runnable check = this::checkPlayback;
    private MediaSessionManager sessions;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;
    private List<String> phrases = Collections.emptyList();
    private long lastNotice;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        reloadRules();
        prefs = getSharedPreferences(RuleRepository.PREFS, MODE_PRIVATE);
        changeListener = (p, key) -> { reloadRules(); scheduleCheck(); };
        prefs.registerOnSharedPreferenceChangeListener(changeListener);
        sessions = getSystemService(MediaSessionManager.class);
        if (sessions == null) return;
        ComponentName self = new ComponentName(this, PlaybackGuardService.class);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, self, handler);
            track(sessions.getActiveSessions(self));
            connected = true;
        } catch (SecurityException notGranted) {
            // Notification access was revoked between binding and this call.
            connected = false;
        }
    }

    @Override public void onListenerDisconnected() {
        release();
        // Ask Android to bind again; it only happens while access stays granted.
        requestRebind(new ComponentName(this, PlaybackGuardService.class));
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        connected = false;
        lastSessionEventElapsed = 0L;
        handler.removeCallbacks(check);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsListener);
        for (Map.Entry<MediaSession.Token, MediaController> entry : controllers.entrySet())
            entry.getValue().unregisterCallback(callbacks.get(entry.getKey()));
        controllers.clear();
        callbacks.clear();
        if (prefs != null && changeListener != null)
            prefs.unregisterOnSharedPreferenceChangeListener(changeListener);
        prefs = null;
    }

    private void reloadRules() {
        List<String> values = new ArrayList<>();
        for (Rule rule : new RuleRepository(this).getAll())
            if (rule.type == Rule.Type.KEYWORD) values.add(rule.value);
        phrases = Collections.unmodifiableList(values);
    }

    private void track(List<MediaController> active) {
        Map<MediaSession.Token, MediaController> current = new HashMap<>();
        if (active != null) {
            for (MediaController controller : active) {
                if (PlaybackRules.supportsPackage(controller.getPackageName(), getPackageName()))
                    current.put(controller.getSessionToken(), controller);
            }
        }
        Iterator<Map.Entry<MediaSession.Token, MediaController>> it =
                controllers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MediaSession.Token, MediaController> entry = it.next();
            if (current.containsKey(entry.getKey())) continue;
            entry.getValue().unregisterCallback(callbacks.remove(entry.getKey()));
            it.remove();
        }
        for (Map.Entry<MediaSession.Token, MediaController> entry : current.entrySet()) {
            if (controllers.containsKey(entry.getKey())) continue;
            MediaController.Callback callback = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata metadata) {
                    scheduleCheck();
                }
                @Override public void onPlaybackStateChanged(PlaybackState state) {
                    scheduleCheck();
                }
            };
            entry.getValue().registerCallback(callback, handler);
            controllers.put(entry.getKey(), entry.getValue());
            callbacks.put(entry.getKey(), callback);
        }
        scheduleCheck();
    }

    private void scheduleCheck() {
        handler.removeCallbacks(check);
        handler.postDelayed(check, CHECK_DELAY_MS);
    }

    private void checkPlayback() {
        for (MediaController controller : controllers.values()) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) continue;
            // Lets the parent confirm on the dashboard that this device publishes sessions.
            lastSessionEventElapsed = SystemClock.elapsedRealtime();
            if (phrases.isEmpty() || !isPlaying(controller.getPlaybackState())
                    || !PlaybackRules.metadataMatches(fields(metadata), phrases)) continue;
            // Pausing again on every PLAYING state also defeats pressing play again.
            controller.getTransportControls().pause();
            GuardControl.leavePlayerIfForeground(controller.getPackageName());
            notice();
        }
    }

    /** Unknown state counts as playing: pausing an already paused video is harmless. */
    private static boolean isPlaying(PlaybackState state) {
        if (state == null) return true;
        switch (state.getState()) {
            case PlaybackState.STATE_NONE:
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
            case PlaybackState.STATE_ERROR:
                return false;
            default:
                return true;
        }
    }

    private static List<CharSequence> fields(MediaMetadata metadata) {
        List<CharSequence> values = new ArrayList<>();
        for (String key : new String[] {
                MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION
        }) {
            values.add(metadata.getText(key));
        }
        MediaDescription description = metadata.getDescription();
        if (description != null) {
            values.add(description.getTitle());
            values.add(description.getSubtitle());
            values.add(description.getDescription());
        }
        return values;
    }

    private void notice() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotice < NOTICE_INTERVAL_MS) return;
        lastNotice = now;
        Toast.makeText(this, "Vídeo bloqueado", Toast.LENGTH_SHORT).show();
    }
}
