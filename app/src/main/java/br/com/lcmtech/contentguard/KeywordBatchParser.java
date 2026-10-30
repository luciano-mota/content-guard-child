package br.com.lcmtech.contentguard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Splits bulk keyword input without splitting multi-word phrases. Pure Java for unit testing. */
public final class KeywordBatchParser {
    private static final int MAX_PER_BATCH = 500;
    private KeywordBatchParser() {}

    /**
     * Commas (and pasted line breaks) separate entries. Spaces inside a phrase are preserved.
     * Empty values are ignored; equivalent words differing by case/accents/punctuation
     * are retained just once. Invalid entries reject the entire batch.
     */
    public static List<String> parse(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : rawInput.split("[,\\r\\n]", -1)) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            String normalized = RuleMatcher.normalize(value);
            if (value.length() < 2 || value.length() > 180 || normalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "Cada palavra ou expressão deve ter de 2 a 180 caracteres.");
            }
            if (seen.add(normalized)) {
                result.add(value);
                if (result.size() > MAX_PER_BATCH) {
                    throw new IllegalArgumentException("Limite de 500 palavras por cadastro.");
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}
