import br.com.lcmtech.contentguard.SocialSearchRules;

public final class SocialSearchRulesTest {
    private static int passed;
    private static void check(boolean expression, String name) {
        if (!expression) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) {
        check(SocialSearchRules.supportsPackage("com.zhiliaoapp.musically"),
                "TikTok Brazil package");
        check(SocialSearchRules.supportsPackage("com.ss.android.ugc.trill"),
                "TikTok regional package");
        check(SocialSearchRules.supportsPackage("com.kwai.video"),
                "Kwai Brazil package");
        check(!SocialSearchRules.supportsPackage("com.google.android.youtube"),
                "does not change YouTube treatment");
        check(!SocialSearchRules.supportsPackage("com.fake.kwai.video"),
                "does not allow arbitrary packages");
        check(!SocialSearchRules.supportsPackage(null), "null package");
        check(SocialSearchRules.isSearchFieldLabel(
                "com.kwai.video:id/search_edit_text"), "Kwai search resource id");
        check(SocialSearchRules.isSearchFieldLabel(
                "com.zhiliaoapp.musically:id/searchBar"), "TikTok camelCase ID");
        check(SocialSearchRules.isSearchFieldLabel("Pesquisar vídeos"),
                "Portuguese search hint");
        check(SocialSearchRules.isSearchFieldLabel("Buscar"),
                "Portuguese alternate hint");
        check(SocialSearchRules.isSearchFieldLabel("Search TikTok"),
                "English search hint");
        check(!SocialSearchRules.isSearchFieldLabel("Escreva um comentário"),
                "comment field not treated as search");
        check(!SocialSearchRules.isSearchFieldLabel("Enviar mensagem"),
                "private message not treated as search");
        check(!SocialSearchRules.isSearchFieldLabel("Editar perfil"),
                "profile edit not treated as search");
        check(!SocialSearchRules.isSearchFieldLabel(""),
                "empty hint not treated as search");
        System.out.println("SocialSearchRules: " + passed + " tests passed.");
    }
}
