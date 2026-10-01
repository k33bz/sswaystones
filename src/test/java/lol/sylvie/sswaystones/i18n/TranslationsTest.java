/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every shipped locale must be complete and format-compatible with en_us. */
class TranslationsTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/data/sswaystones/lang");
    private static final Path SOURCE_DIR = Path.of("src/main/java");

    // Top-10 game/web languages plus regional variants Minecraft does not fall back
    // between
    static final List<String> LOCALES = List.of("zh_cn", "zh_tw", "ru_ru", "es_es", "es_mx", "pt_br", "de_de", "ja_jp",
            "fr_fr", "fr_ca", "ko_kr", "pl_pl");

    // Values that may legitimately read the same as English (pure format strings,
    // loanwords)
    private static final Set<String> MAY_MATCH_ENGLISH = Set.of("command.sswaystones.config_format",
            "gui.sswaystones.toggle_global", "gui.sswaystones.access_global", "gui.sswaystones.access_team");

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");
    private static final Pattern FORMAT_CODE = Pattern.compile("§[0-9a-fk-or]");
    private static final Pattern KEY_IN_SOURCE = Pattern
            .compile("\"((?:gui|error|message|command|config)\\.sswaystones\\.[a-z_]+)\"");

    static Stream<String> locales() {
        return LOCALES.stream();
    }

    @ParameterizedTest
    @MethodSource("locales")
    void localeHasExactlyTheEnglishKeys(String locale) throws IOException {
        Set<String> english = load("en_us").keySet();
        Set<String> translated = load(locale).keySet();

        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(translated);
        Set<String> extra = new TreeSet<>(translated);
        extra.removeAll(english);

        assertTrue(missing.isEmpty() && extra.isEmpty(), locale + " missing=" + missing + " extra=" + extra);
    }

    @ParameterizedTest
    @MethodSource("locales")
    void valuesAreTranslatedAndKeepPlaceholders(String locale) throws IOException {
        Map<String, String> english = load("en_us");
        Map<String, String> translated = load(locale);
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> e : translated.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            String source = english.get(key);
            if (source == null)
                continue;
            if (value.isBlank())
                problems.add(key + " is blank");
            else if (value.equals(source) && !MAY_MATCH_ENGLISH.contains(key))
                problems.add(key + " is untranslated");
            if (!matches(PLACEHOLDER, source).equals(matches(PLACEHOLDER, value)))
                problems.add(key + " placeholders differ");
            if (!matches(FORMAT_CODE, source).equals(matches(FORMAT_CODE, value)))
                problems.add(key + " § codes differ");
        }
        assertTrue(problems.isEmpty(), locale + ": " + problems);
    }

    @Test
    void everyKeyUsedInCodeExistsInEnglish() throws IOException {
        Set<String> english = load("en_us").keySet();
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = KEY_IN_SOURCE.matcher(Files.readString(p));
                while (m.find())
                    used.add(m.group(1));
            }
        }
        used.removeAll(english);
        assertTrue(used.isEmpty(), "keys used in code but missing from en_us: " + used);
    }

    @Test
    void englishHasNoDuplicateKeys() throws IOException {
        load("en_us");
    }

    @Test
    void placeholderMatcherCountsPositionalArguments() {
        assertEquals(List.of("%1$s", "%2$s"), matches(PLACEHOLDER, "%1$s = %2$s"));
        assertEquals(List.of("§b", "§e"), matches(FORMAT_CODE, "§b§eList"));
    }

    private static List<String> matches(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find())
            found.add(m.group());
        found.sort(null);
        return found;
    }

    // Strict UTF-8 + duplicate-key detection (Gson's object parsing silently keeps
    // the last duplicate)
    private static Map<String, String> load(String locale) throws IOException {
        Path file = LANG_DIR.resolve(locale + ".json");
        assertTrue(Files.exists(file), "missing lang file " + file);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString();
        } catch (CharacterCodingException e) {
            return fail(locale + " is not valid UTF-8");
        }
        Map<String, String> out = new LinkedHashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                String key = reader.nextName();
                if (out.put(key, reader.nextString()) != null)
                    fail(locale + " has duplicate key " + key);
            }
            reader.endObject();
        }
        return out;
    }
}
