import br.com.lcmtech.contentguard.RuleMatcher;

public final class RuleMatcherTest {
    private static void assertTrue(boolean outcome, String name) {
        if (!outcome) throw new AssertionError("FAILED: " + name);
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) {
        assertTrue(RuleMatcher.containsPhrase("Assistir Bob-Esponja", "bob esponja"), "accent/case/punctuation");
        assertTrue(RuleMatcher.containsPhrase("O SPONGEBOB está no vídeo", "spongebob"), "english title");
        assertTrue(RuleMatcher.containsPhrase("Violência infantil", "violencia"), "diacritics");
        assertTrue(!RuleMatcher.containsPhrase("Bobby é legal", "bob"), "does not split words");
        assertTrue(!RuleMatcher.containsPhrase("desenho educativo", "bob esponja"), "unrelated content allowed");
        assertTrue(RuleMatcher.matchesDomain("https://www.exemplo.com/path", "exemplo.com"), "domain subdomain");
        assertTrue(RuleMatcher.matchesDomain("exemplo.com/search", "exemplo.com"), "domain without scheme");
        assertTrue(!RuleMatcher.matchesDomain("https://exemplo.com.evil.test/", "exemplo.com"), "anti spoofing suffix");
        assertTrue(!RuleMatcher.matchesDomain("https://naoexemplo.com/", "exemplo.com"), "anti spoofing prefix");
        assertTrue(!RuleMatcher.matchesDomain("outra coisa", "exemplo.com"), "text not a domain");
        assertTrue(RuleMatcher.matchesDomain("https://exemplo.com", "www.exemplo.com"), "www domain treated as root");
        assertTrue(RuleMatcher.matchesGoogleSearchUrl(
                "https://www.google.com/search?q=Bob+Esponja&sourceid=chrome", "Bob Esponja"),
                "history suggestion submitted query with +");
        assertTrue(RuleMatcher.matchesGoogleSearchUrl(
                "https://www.google.com.br/search?q=Bob%20Esponja", "bob-esponja"),
                "percent-encoded Google Brazil search");
        assertTrue(RuleMatcher.matchesGoogleSearchUrl(
                "https://google.com/search?q=SPONGEBOB", "SpongeBob"),
                "Google search case");
        assertTrue(!RuleMatcher.matchesGoogleSearchUrl(
                "https://google.com/search?q=Tom+e+Jerry", "Bob Esponja"),
                "permitted search query");
        assertTrue(!RuleMatcher.matchesGoogleSearchUrl(
                "https://google.com.evil.test/search?q=Bob+Esponja", "Bob Esponja"),
                "only trusted Google hosts");
        assertTrue(!RuleMatcher.matchesGoogleSearchUrl(
                "https://www.google.com/maps?q=Bob+Esponja", "Bob Esponja"),
                "non-search Google page");
        assertTrue(!RuleMatcher.matchesGoogleSearchUrl(
                "https://www.youtube.com/watch?v=BobEsponja", "Bob Esponja"),
                "do not infer video metadata from URL");
        assertTrue(!RuleMatcher.matchesGoogleSearchUrl(
                "https://www.google.com/search?q=%XX", "Bob Esponja"),
                "invalid escaped URL safely ignored");
    }
}
