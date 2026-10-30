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
    }
}
