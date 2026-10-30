package br.com.lcmtech.contentguard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public final class MainActivity extends Activity {
    private PinStore pinStore;
    private RuleRepository repository;
    private boolean authenticated = false;
    private boolean dialogOpen = false;
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pinStore = new PinStore(this);
        repository = new RuleRepository(this);
        showLocked();
        getWindow().getDecorView().post(this::requestAuth);
    }

    @Override protected void onResume() {
        super.onResume();
        if (authenticated) showDashboard();
        else if (!dialogOpen && pinStore != null)
            getWindow().getDecorView().post(this::requestAuth);
    }

    @Override protected void onStop() {
        super.onStop();
        authenticated = false;
        showLocked();
    }

    private void initializeLayout() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        content.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(content);
        setContentView(scroll);
    }

    private void showLocked() {
        initializeLayout();
        addHeading("ContentGuard", 27);
        addBody("Controle de conteúdo local. Acesse com o PIN da responsável para gerenciar as regras.");
    }

    private void requestAuth() {
        if (authenticated || dialogOpen || isFinishing()) return;
        if (pinStore.hasPin()) requestExistingPin();
        else requestNewPin();
    }

    private EditText pinEntry() {
        EditText text = new EditText(this);
        text.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        text.setHint("PIN de 4 a 8 dígitos");
        text.setPadding(dp(24), dp(15), dp(24), dp(15));
        return text;
    }

    private void requestNewPin() {
        dialogOpen = true;
        EditText entry = pinEntry();
        new AlertDialog.Builder(this)
                .setTitle("Criar PIN da responsável")
                .setMessage("Protege a edição das regras. Guarde o PIN: esta versão não tem recuperação automática.")
                .setView(entry)
                .setCancelable(false)
                .setPositiveButton("Continuar", (d, button) -> {
                    dialogOpen = false;
                    String first = entry.getText().toString();
                    if (!pinStore.isValidFormat(first)) {
                        toast("Utilize de 4 a 8 dígitos.");
                        requestNewPin();
                    } else confirmNewPin(first);
                })
                .show();
    }

    private void confirmNewPin(String first) {
        dialogOpen = true;
        EditText entry = pinEntry();
        new AlertDialog.Builder(this)
                .setTitle("Confirme o PIN")
                .setView(entry)
                .setCancelable(false)
                .setPositiveButton("Salvar", (d, button) -> {
                    dialogOpen = false;
                    if (!first.equals(entry.getText().toString()) || !pinStore.createPin(first)) {
                        toast("Os PINs não conferem. Tente novamente.");
                        requestNewPin();
                    } else {
                        authenticated = true;
                        showDashboard();
                    }
                })
                .show();
    }

    private void requestExistingPin() {
        dialogOpen = true;
        EditText entry = pinEntry();
        new AlertDialog.Builder(this)
                .setTitle("PIN da responsável")
                .setView(entry)
                .setCancelable(false)
                .setPositiveButton("Entrar", (d, button) -> {
                    dialogOpen = false;
                    if (pinStore.verify(entry.getText().toString())) {
                        authenticated = true;
                        showDashboard();
                    } else {
                        toast("PIN incorreto.");
                        requestExistingPin();
                    }
                })
                .show();
    }

    private void showDashboard() {
        if (!authenticated) return;
        initializeLayout();
        addHeading("ContentGuard", 27);
        addBody("Bloqueio local por regras • Android 0.6 (bloqueio de reprodução)");
        addHeading("1. Ativar proteção", 19);
        addBody(isServiceEnabled() ? "✓ Serviço de acessibilidade ativado" :
                "⚠ Serviço de acessibilidade desativado");
        addButton("Abrir configurações de acessibilidade", () -> showConsentDialog());
        addHeading("2. Bloqueio de reprodução", 19);
        addBody(PlaybackGuardService.isGranted(this)
                ? "✓ Vídeos com palavras bloqueadas no título ou no canal são pausados " +
                  "em qualquer player (YouTube, YouTube Kids, streaming)."
                : "⚠ Desativado: vídeos abertos pelo feed, autoplay ou links não são barrados.");
        addButton("Autorizar bloqueio de reprodução", () ->
                GuardSetup.openNotificationAccess(this));
        addHeading("3. Proteção contra desativação", 19);
        addBody("Telas do Android que permitem desativar, forçar parada ou desinstalar " +
                "o ContentGuard são fechadas. " + GuardSetup.settingsGuardStatus(this));
        addButton("Liberar Configurações por 15 minutos", () -> {
            GuardControl.pauseSettingsGuard(this);
            showDashboard();
        });
        addButton("Bloquear Configurações agora", () -> {
            GuardControl.resumeSettingsGuard(this);
            showDashboard();
        });
        addHeading("4. Regras de bloqueio", 19);
        addBody("PALAVRA: qualquer desenho, personagem, canal ou tema indesejado. Vale " +
                "para pesquisas e sugestões no YouTube, Google e navegadores, pesquisas do " +
                "TikTok e Kwai, títulos no player do YouTube e título/canal de vídeos " +
                "em reprodução. Cadastre também nomes de personagens e de canais. " +
                "DOMÍNIO: endereço digitado em navegadores compatíveis. " +
                "APP: pacote do aplicativo bloqueado ao abrir.");
        addButton("+ Adicionar regra", this::chooseRuleType);
        List<Rule> rules = repository.getAll();
        if (rules.isEmpty()) addBody("Nenhuma regra cadastrada. Exemplo: PALAVRA → " +
                "Bob Esponja, Patrick Estrela, Lula Molusco.");
        for (Rule rule : rules) {
            Button item = new Button(this);
            item.setAllCaps(false);
            item.setText(typeLabel(rule.type) + ":  " + rule.value + "     ✕");
            item.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Remover bloqueio?")
                    .setMessage(rule.value)
                    .setPositiveButton("Remover", (d, w) -> {
                        repository.remove(rule.id);
                        showDashboard();
                    })
                    .setNegativeButton("Cancelar", null).show());
            content.addView(item);
        }
        addHeading("Privacidade e limitações", 19);
        addBody("As regras são armazenadas no dispositivo. Este aplicativo não possui " +
                "permissão de internet e não registra pesquisas ou histórico de navegação. " +
                "A API de acessibilidade pode ver textos presentes nas telas dos apps monitorados, " +
                "mediante autorização da responsável.");
        addBody("O filtro do TikTok/Kwai depende dos campos de pesquisa acessíveis; " +
                "não filtra o feed de vídeos, comentários nem mensagens privadas. " +
                "Não garante ocultar miniaturas, sugestões ou vídeos antes de aparecerem. " +
                "Links abertos sem digitar a URL podem escapar do filtro de domínios. " +
                "Vídeos cujo título e canal não contêm nenhuma palavra cadastrada não são " +
                "reconhecidos. Modo de segurança, ADB e restauração de fábrica " +
                "desativam a proteção.");
    }

    private void showConsentDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Uso da acessibilidade")
                .setMessage("Com sua autorização, o ContentGuard observa eventos de interface " +
                        "do YouTube, TikTok, Kwai e navegadores compatíveis, incluindo " +
                        "campos de pesquisa e sugestões selecionadas. Isso é necessário " +
                        "para tentar interromper conteúdos proibidos. Nenhum desses textos " +
                        "é salvo ou enviado. O serviço pode ser desligado nas Configurações.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Entendi, configurar", (d, w) ->
                        GuardSetup.openAccessibilitySettings(this))
                .show();
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(this, GuardAccessibilityService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ComponentName component = ComponentName.unflattenFromString(info.getId());
            if (expected.equals(component)) return true;
        }
        return false;
    }

    private void chooseRuleType() {
        String[] types = {"Palavra ou expressão", "Domínio / site", "Aplicativo (nome do pacote)"};
        new AlertDialog.Builder(this)
                .setTitle("Tipo de bloqueio")
                .setItems(types, (dialog, index) -> {
                    Rule.Type type = Rule.Type.values()[index];
                    addRuleDialog(type);
                }).show();
    }

    private void addRuleDialog(Rule.Type type) {
        boolean keyword = type == Rule.Type.KEYWORD;
        EditText input = new EditText(this);
        input.setHint(keyword ? "Bob Esponja, SpongeBob, SquarePants" :
                type == Rule.Type.DOMAIN ? "ex.: exemplo.com" : "ex.: com.exemplo.app");
        input.setPadding(dp(22), dp(15), dp(22), dp(15));
        if (keyword) {
            input.setSingleLine(false);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(3);
            input.setMaxLines(6);
            input.setGravity(Gravity.TOP);
        } else {
            input.setSingleLine(true);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(keyword ? "Adicionar palavras ou expressões" :
                        "Adicionar " + typeLabel(type).toLowerCase())
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Adicionar", null);
        if (keyword) {
            builder.setMessage("Separe palavras ou expressões por vírgulas. " +
                    "Cada item vira uma regra independente. Repetidos são ignorados.");
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (keyword) {
                        try {
                            int added = repository.addKeywords(value);
                            if (added == 0) {
                                toast("Nenhuma regra nova: confira os termos ou as duplicatas.");
                                return;
                            }
                            dialog.dismiss();
                            showDashboard();
                            toast(added == 1 ? "1 palavra adicionada." :
                                    added + " palavras adicionadas.");
                        } catch (IllegalArgumentException error) {
                            toast(error.getMessage());
                        }
                        return;
                    }

                    boolean valid = value.length() >= 2 && value.length() <= 180
                            && value.matches("[A-Za-z0-9_.-]+") && value.contains(".");
                    if (!valid) {
                        toast("Valor inválido. Verifique o formato e tente novamente.");
                        return;
                    }
                    repository.add(new Rule(type, value));
                    dialog.dismiss();
                    showDashboard();
                }));
        dialog.show();
    }

    private String typeLabel(Rule.Type type) {
        return switch (type) {
            case KEYWORD -> "PALAVRA";
            case DOMAIN -> "DOMÍNIO";
            case APP -> "APP";
        };
    }

    private int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + .5f); }
    private void addHeading(String text, int sp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(15, 23, 42));
        view.setTextSize(sp);
        view.setPadding(0, dp(14), 0, dp(8));
        content.addView(view);
    }
    private void addBody(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(55, 65, 81));
        view.setPadding(0, dp(5), 0, dp(10));
        content.addView(view);
    }
    private void addButton(String text, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        content.addView(button);
    }
    private void toast(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show();
    }
}
