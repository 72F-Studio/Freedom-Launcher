import com.studio72f.eigen.CategoryRules;
import java.util.Map;
public class CategoryRulesTest {
    public static void main(String[] args) {
        check("games", CategoryRules.classify("example.arcade", 0));
        check("tools", CategoryRules.classify("example.unknown", -1));
        check("photos", CategoryRules.classify("example.camera", -1));
        check("social", CategoryRules.classify("org.telegram.messenger", -1));
        check("E", CategoryRules.initial("Éclair"));
        check("#", CategoryRules.initial("123 Player"));
        check("#", CategoryRules.initial("日本語"));
        check("work", CategoryRules.resolve("social", "work", Map.of()));
        check("leisure", CategoryRules.resolve("music", null, Map.of("music","media","media","leisure")));
        check("music", CategoryRules.resolve("music", null, Map.of("music","video","video","music")));
        System.out.println("10 category/normalization tests passed");
    }
    static void check(String expected, String actual) { if (!expected.equals(actual)) throw new AssertionError(expected+" != "+actual); }
}
