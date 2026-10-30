import br.com.lcmtech.contentguard.KeywordBatchParser;
import java.util.Arrays;
import java.util.List;

public final class KeywordBatchParserTest {
    private static int passed;
    private static void expect(boolean value, String name) {
        if (!value) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("PASS: " + name);
    }
    private static void equalsList(List<String> expected, List<String> actual, String name) {
        expect(expected.equals(actual), name + ": expected=" + expected + ", actual=" + actual);
    }
    private static void invalid(String input, String name) {
        try {
            KeywordBatchParser.parse(input);
            throw new AssertionError("FAILED: " + name);
        } catch (IllegalArgumentException expected) {
            expect(true, name);
        }
    }
    public static void main(String[] args) {
        equalsList(Arrays.asList("Bob Esponja", "SpongeBob", "SquarePants"),
                KeywordBatchParser.parse("Bob Esponja, SpongeBob, SquarePants"),
                "split comma and trim");
        equalsList(Arrays.asList("Bob Esponja", "SpongeBob"),
                KeywordBatchParser.parse(",, Bob Esponja, , SpongeBob ,,"),
                "ignore empty items");
        equalsList(Arrays.asList("Bob Esponja", "SpongeBob"),
                KeywordBatchParser.parse("Bob Esponja, bob-esponja, BÓB ESponja, SpongeBob"),
                "deduplicate accent/case/punctuation");
        equalsList(Arrays.asList("Bob Esponja", "SpongeBob"),
                KeywordBatchParser.parse("Bob Esponja\nSpongeBob"),
                "support pasted newline");
        equalsList(Arrays.asList("Bob Esponja"),
                KeywordBatchParser.parse("Bob Esponja"),
                "keep single multiword phrase");
        equalsList(Arrays.asList(), KeywordBatchParser.parse(",, , "), "blank batch");
        equalsList(Arrays.asList(), KeywordBatchParser.parse(null), "null batch");
        invalid("a, SpongeBob", "reject too-short item without partial addition");
        invalid("ok," + "x".repeat(181), "reject oversized item");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            if (i > 0) b.append(',');
            b.append("term").append(i);
        }
        invalid(b.toString(), "protect against excessively large batch");
        System.out.println("KeywordBatchParser: " + passed + " tests passed.");
    }
}
