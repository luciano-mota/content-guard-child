package br.com.lcmtech.contentguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores only rule definitions. NEVER stores captured searches, UI text or browsing history. */
public final class RuleRepository {
    public static final String PREFS = "content_guard_rules";
    private static final String KEY = "rules_json";
    private final SharedPreferences preferences;

    public RuleRepository(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Rule> getAll() {
        String saved = preferences.getString(KEY, "[]");
        List<Rule> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(saved);
            for (int i = 0; i < rows.length(); i++) {
                try { result.add(Rule.fromJson(rows.getJSONObject(i))); }
                catch (Exception ignored) { /* Skip malformed entries. */ }
            }
        } catch (Exception ignored) { /* No rules when preference is corrupted. */ }
        return Collections.unmodifiableList(result);
    }

    public void add(Rule rule) {
        List<Rule> rules = new ArrayList<>(getAll());
        rules.add(rule);
        save(rules);
    }

    public void remove(String id) {
        List<Rule> rules = new ArrayList<>(getAll());
        rules.removeIf(rule -> rule.id.equals(id));
        save(rules);
    }

    private void save(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            try { array.put(rule.toJson()); } catch (Exception ignored) {}
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }
}
