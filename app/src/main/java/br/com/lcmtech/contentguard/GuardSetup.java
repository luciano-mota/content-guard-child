package br.com.lcmtech.contentguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/**
 * Opens the system screens the adult needs during setup. Only called from
 * PIN-authenticated dashboards, so each one first pauses the anti-tamper
 * guard; otherwise it would leave the very screen being configured.
 */
final class GuardSetup {
    private GuardSetup() {}

    static void openAccessibilitySettings(Activity activity) {
        GuardControl.pauseSettingsGuard(activity);
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception error) {
            new AlertDialog.Builder(activity).setTitle("Acessibilidade")
                    .setMessage("Abra Configurações → Acessibilidade e ative o ContentGuard.")
                    .setPositiveButton("OK", null).show();
        }
    }

    /** ADB fallback for TVs whose settings do not show "Notification access". */
    static String adbGrantCommand(Activity activity) {
        return "adb shell cmd notification allow_listener "
                + new ComponentName(activity, PlaybackGuardService.class).flattenToString();
    }

    static void openNotificationAccess(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Autorizar bloqueio de reprodução")
                .setMessage("Ative \"ContentGuard\" em Acesso a notificações. Com isso o "
                        + "ContentGuard lê apenas o título e o canal do vídeo que está "
                        + "tocando e pausa vídeos bloqueados. Notificações não são lidas "
                        + "nem salvas.\n\nSe a opção não aparecer (comum em TVs), use um "
                        + "computador com ADB:\n" + adbGrantCommand(activity))
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Abrir configurações", (d, w) -> {
                    GuardControl.pauseSettingsGuard(activity);
                    if (!tryStart(activity, notificationAccessIntent(activity)))
                        tryStart(activity, new Intent(
                                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                })
                .show();
    }

    private static Intent notificationAccessIntent(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        new ComponentName(activity, PlaybackGuardService.class)
                                .flattenToString());
    }

    private static boolean tryStart(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    /** Status line for the anti-tamper guard. */
    static String settingsGuardStatus(Activity activity) {
        long remaining = GuardControl.settingsPauseRemainingMs(activity);
        if (remaining <= 0) return "Configurações do ContentGuard bloqueadas para a criança.";
        long minutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
        return "Configurações liberadas por mais " + minutes + " min.";
    }
}
