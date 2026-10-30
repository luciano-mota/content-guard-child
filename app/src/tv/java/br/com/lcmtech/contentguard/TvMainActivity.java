package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Separate D-pad-friendly configuration activity. No backend, browsers or tracking. */
public final class TvMainActivity extends Activity {
    private PinStore pinStore;
    private RuleRepository repository;
    private boolean authenticated;
    private boolean authDialogShowing;
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pinStore = new PinStore(this);
        repository = new RuleRepository(this);
        showLocked();
        getWindow().getDecorView().post(this::requestAuth);
    }

    @Override protected void onResume() {
        super.onResume();
        if (authenticated) showDashboard();
        else if (!authDialogShowing && pinStore != null)
            getWindow().getDecorView().post(this::requestAuth);
    }

    @Override protected void onStop() {
        super.onStop();
        authenticated = false;
        showLocked();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void renderFrame() {
        LinearLayout frame = new LinearLayout(this);
        frame.setBackgroundColor(Color.rgb(5, 23, 54));
        frame.setOrientation(LinearLayout.HORIZONTAL);
        frame.setPadding(dp(46), dp(30), dp(46), dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(1), dp(22), dp(20));
        scroll.addView(content);
        frame.addView(scroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 3.0f));

        ImageView cover = new ImageView(this);
        cover.setImageResource(R.drawable.contentguard_opening);
        cover.setContentDescription("ContentGuard Child: proteção inteligente de conteúdo");
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(cover, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 2.0f));

        setContentView(frame);
    }

    private void text(String value, int sp, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sp);
        label.setTextColor(color);
        label.setPadding(dp(2), dp(4), 0, dp(13));
        content.addView(label);
    }

    private void button(String label, Runnable action) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(18);
        btn.setAllCaps(false);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        btn.setMinHeight(dp(60));
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(18, 92, 147)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(10);
        content.addView(btn, p);
        btn.setOnFocusChangeListener((v, focused) -> btn.setBackgroundTintList(
                ColorStateList.valueOf(focused ? Color.rgb(14, 166, 196)
                        : Color.rgb(18, 92, 147))));
        btn.setOnClickListener(v -> action.run());
    }

    private void showLocked() {
        renderFrame();
        text("ContentGuard Child · TV", 30, Color.WHITE);
        text("Proteção de conteúdo no YouTube da Google TV.", 19, Color.LTGRAY);
        text("Use o controle remoto. O acesso às regras requer o PIN da pessoa responsável.",
                16, Color.LTGRAY);
    }

    private EditText pinInput() {
        EditText entry = new EditText(this);
        entry.setHint("PIN de 4 a 8 dígitos");
        entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        entry.setPadding(dp(18), dp(14), dp(18), dp(14));
        return entry;
    }

    private void requestAuth() {
        if (isFinishing() || authenticated || authDialogShowing) return;
        if (pinStore.hasPin()) requestExistingPin();
        else requestNewPin();
    }

    private void requestNewPin() {
        authDialogShowing = true;
        EditText first = pinInput();
        new AlertDialog.Builder(this).setTitle("Criar PIN parental")
                .setMessage("Este PIN protege as regras. Não há recuperação automática nesta versão.")
                .setView(first).setCancelable(false)
                .setPositiveButton("Continuar", (d, w) -> {
                    authDialogShowing = false;
                    String value = first.getText().toString();
                    if (pinStore.isValidFormat(value)) confirmPin(value);
                    else { notifyUser("PIN inválido."); requestNewPin(); }
                }).show();
    }

    private void confirmPin(String original) {
        authDialogShowing = true;
        EditText again = pinInput();
        new AlertDialog.Builder(this).setTitle("Confirmar PIN")
                .setView(again).setCancelable(false)
                .setPositiveButton("Salvar", (d, w) -> {
                    authDialogShowing = false;
                    if (original.equals(again.getText().toString())
                            && pinStore.createPin(original)) {
                        authenticated = true;
                        showDashboard();
                    } else { notifyUser("PINs diferentes."); requestNewPin(); }
                }).show();
    }

    private void requestExistingPin() {
        authDialogShowing = true;
        EditText entry = pinInput();
        new AlertDialog.Builder(this).setTitle("PIN parental")
                .setView(entry).setCancelable(false)
                .setPositiveButton("Entrar", (d, w) -> {
                    authDialogShowing = false;
                    if (pinStore.verify(entry.getText().toString())) {
                        authenticated = true;
                        showDashboard();
                    } else { notifyUser("PIN incorreto."); requestExistingPin(); }
                }).show();
    }

    private boolean isTvServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager)
                getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(this, TvYoutubeAccessibilityService.class);
        for (AccessibilityServiceInfo item : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (expected.equals(ComponentName.unflattenFromString(item.getId()))) return true;
        }
        return false;
    }

    private void showDashboard() {
        if (!authenticated) return;
        renderFrame();
        text("ContentGuard Child · Google TV", 30, Color.WHITE);
        text("Filtro experimental para YouTube (sem navegador).", 18, Color.LTGRAY);
        text(isTvServiceEnabled() ? "Proteção: serviço ATIVADO"
                        : "Proteção: serviço DESATIVADO",
                20, isTvServiceEnabled() ? Color.rgb(102, 237, 190)
                        : Color.rgb(255, 197, 114));
        button("Ativar / verificar Acessibilidade", this::openAccessibility);
        button("+ Adicionar palavras separadas por vírgulas", this::addKeywords);
        List<Rule> all = repository.getAll();
        int count = 0;
        for (Rule rule : all) if (rule.type == Rule.Type.KEYWORD) count++;
        text("Regras de palavras: " + count, 20, Color.WHITE);
        for (Rule rule : all) {
            if (rule.type != Rule.Type.KEYWORD) continue;
            button("Remover: " + rule.value, () ->
                    new AlertDialog.Builder(this).setTitle("Remover bloqueio?")
                            .setMessage(rule.value)
                            .setPositiveButton("Remover", (d, w) -> {
                                repository.remove(rule.id);
                                showDashboard();
                            }).setNegativeButton("Cancelar", null).show());
        }
        text("As regras são armazenadas apenas nesta TV. Não são sincronizadas com o celular.",
                16, Color.LTGRAY);
        text("Limitação: o YouTube para TV pode ocultar títulos dos serviços de "
                        + "Acessibilidade. A reprodução pode começar antes do bloqueio ou não ser "
                        + "identificada. Não substitui a supervisão da responsável.",
                15, Color.LTGRAY);
    }

    private void openAccessibility() {
        new AlertDialog.Builder(this)
                .setTitle("Autorizar leitura de interface")
                .setMessage("Com seu consentimento, o ContentGuard observa apenas eventos do "
                        + "YouTube para TV, como buscas, seleção de vídeos e títulos acessíveis. "
                        + "Esses textos não são armazenados nem enviados. "
                        + "A ativação é manual nas configurações do aparelho e pode ser revogada.")
                .setPositiveButton("Abrir configurações", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                    catch (Exception error) {
                        notifyUser("Abra Acessibilidade nas configurações da TV.");
                    }
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void addKeywords() {
        EditText entry = new EditText(this);
        entry.setHint("Bob Esponja, SpongeBob, SquarePants");
        entry.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entry.setMinLines(3);
        entry.setPadding(dp(18), dp(16), dp(18), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Adicionar palavras")
                .setMessage("Separe por vírgulas ou quebras de linha; até 500 por vez.")
                .setView(entry).setPositiveButton("Adicionar", null)
                .setNegativeButton("Cancelar", null).create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        int count = repository.addKeywords(entry.getText().toString());
                        if (count == 0) {
                            notifyUser("Nenhuma regra nova.");
                        } else {
                            dialog.dismiss();
                            showDashboard();
                            notifyUser(count + " regra(s) adicionada(s).");
                        }
                    } catch (IllegalArgumentException error) {
                        notifyUser(error.getMessage());
                    }
                }));
        dialog.show();
    }

    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
