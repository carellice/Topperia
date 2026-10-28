package it.topperia.game.engine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A historical Top 10, its citation, and the exact accepted answer spellings. */
public final class RankedList {
    public final String id;
    public final String title;
    public final String subtitle;
    public final String category;
    public final String icon;
    public final String sourceName;
    public final String sourceUrl;
    public final String referenceYear;
    public final String methodology;
    public final List<Entry> entries;
    private final Map<String, Integer> answerIndices;

    public RankedList(String id, String title, String subtitle, String category, String icon,
                      String sourceName, String sourceUrl, String referenceYear,
                      String methodology, Entry... entries) {
        this.id = requireText(id, "id");
        this.title = requireText(title, "title");
        this.subtitle = requireText(subtitle, "subtitle");
        this.category = requireText(category, "category");
        this.icon = requireText(icon, "icon");
        this.sourceName = requireText(sourceName, "sourceName");
        this.sourceUrl = requireText(sourceUrl, "sourceUrl");
        this.referenceYear = requireText(referenceYear, "referenceYear");
        this.methodology = requireText(methodology, "methodology");
        if (entries == null || entries.length != 10) {
            throw new IllegalArgumentException("A Top 10 must contain exactly ten entries");
        }
        this.entries = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(entries)));
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < entries.length; i++) {
            Entry entry = entries[i];
            if (entry == null) throw new IllegalArgumentException("An entry cannot be null");
            addAnswer(indices, entry.name, i);
            for (String alias : entry.aliases) addAnswer(indices, alias, i);
        }
        answerIndices = Collections.unmodifiableMap(indices);
    }

    private static void addAnswer(Map<String, Integer> indices, String answer, int index) {
        String normalized = normalize(answer);
        Integer previous = indices.put(normalized, index);
        if (previous != null && previous != index) {
            throw new IllegalArgumentException("An answer spelling belongs to two entries: " + answer);
        }
    }

    /** Case, accents, whitespace, and punctuation never make an otherwise exact answer wrong. */
    public static String normalize(String input) {
        if (input == null) return "";
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    /** Zero-based position, or -1 for a spelling absent from this ranking. */
    public int indexOf(String input) {
        Integer index = answerIndices.get(normalize(input));
        return index == null ? -1 : index;
    }

    static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    public static final class Entry {
        public final String name;
        public final String value;
        public final List<String> aliases;

        public Entry(String name, String value, String... aliases) {
            this.name = requireText(name, "name");
            this.value = requireText(value, "value");
            if (aliases == null) throw new IllegalArgumentException("aliases must not be null");
            List<String> copy = new ArrayList<>();
            for (String alias : aliases) copy.add(requireText(alias, "alias"));
            this.aliases = Collections.unmodifiableList(copy);
        }
    }
}
