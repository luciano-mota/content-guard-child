import br.com.lcmtech.contentguard.PlaybackRules;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PlaybackRulesTest {
    private static int passed;
    private static void check(boolean expression, String name) {
        if (!expression) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) {
        String own = "br.com.lcmtech.contentguard.tv";
        List<String> rules = Arrays.asList("Bob Esponja", "SpongeBob");
        check(PlaybackRules.supportsPackage("com.google.android.youtube.tv", own),
                "YouTube for Google TV");
        check(PlaybackRules.supportsPackage("com.google.android.youtube.tvkids", own),
                "YouTube Kids for Google TV");
        check(PlaybackRules.supportsPackage("com.google.android.apps.youtube.kids", own),
                "YouTube Kids on the phone");
        check(!PlaybackRules.supportsPackage(own, own), "ignores ContentGuard itself");
        check(!PlaybackRules.supportsPackage(null, own), "null package");
        check(PlaybackRules.metadataMatches(
                Arrays.asList("Bob Esponja | Episódio completo", "Nickelodeon"), rules),
                "blocked title");
        check(PlaybackRules.metadataMatches(
                Arrays.asList("Melhores momentos", "Bob Esponja em Português"), rules),
                "blocked channel name");
        check(PlaybackRules.metadataMatches(
                Arrays.asList(null, "", "SpongeBob SquarePants"), rules),
                "skips empty fields");
        check(!PlaybackRules.metadataMatches(
                Arrays.asList("Tom e Jerry", "Cartoon Network"), rules),
                "permitted video plays");
        check(!PlaybackRules.metadataMatches(Arrays.asList("Bob", "Esponja"), rules),
                "fields are not concatenated");
        check(!PlaybackRules.metadataMatches(
                Collections.singletonList("Bob Esponja"), Collections.emptyList()),
                "no rules, no block");
        check(!PlaybackRules.metadataMatches(null, rules), "no metadata");
        System.out.println("PlaybackRules: " + passed + " tests passed.");
    }
}
