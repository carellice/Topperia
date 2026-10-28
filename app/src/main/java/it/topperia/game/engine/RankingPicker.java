package it.topperia.game.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** A random bag per candidate pool. Callers may pass an already category-filtered list. */
public final class RankingPicker {
    private final Random random;
    private final Map<List<String>, Set<String>> remainingByPool = new HashMap<>();

    public RankingPicker() { this(new Random()); }

    /** Inject a seeded Random to reproduce a sequence in tests. */
    public RankingPicker(Random random) {
        if (random == null) throw new IllegalArgumentException("Random must not be null");
        this.random = random;
    }

    /**
     * Prefer unfinished rankings among those not yet drawn in the current bag.
     * Once every candidate has been drawn, refill the bag. Candidate ordering does
     * not affect the bag identity; separate category pools retain separate bags.
     * lastId also accounts for a ranking entered directly or from another pool.
     * A sole candidate is allowed to repeat; an empty candidate list is an error.
     */
    public RankedList pick(List<RankedList> candidates, Set<String> playedIds, String lastId) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one ranking is required");
        }
        Map<String, RankedList> byId = new LinkedHashMap<>();
        for (RankedList ranking : candidates) {
            if (ranking == null) throw new IllegalArgumentException("A candidate must not be null");
            if (byId.put(ranking.id, ranking) != null) {
                throw new IllegalArgumentException("Candidate IDs must be unique: " + ranking.id);
            }
        }
        List<String> poolKey = new ArrayList<>(byId.keySet());
        Collections.sort(poolKey);
        Set<String> remaining = remainingByPool.get(poolKey);
        if (remaining == null) {
            remaining = new LinkedHashSet<>(poolKey);
            remainingByPool.put(poolKey, remaining);
        }
        // A direct selection or a draw in an overlapping category counts as seen.
        if (lastId != null) remaining.remove(lastId);
        if (remaining.isEmpty()) remaining.addAll(poolKey);

        List<String> eligible = new ArrayList<>();
        List<String> unseen = new ArrayList<>();
        for (String id : remaining) {
            if (poolKey.size() > 1 && id.equals(lastId)) continue;
            eligible.add(id);
            if (playedIds == null || !playedIds.contains(id)) unseen.add(id);
        }
        List<String> choices = unseen.isEmpty() ? eligible : unseen;
        String selected = choices.get(random.nextInt(choices.size()));
        remaining.remove(selected);
        return byId.get(selected);
    }
}
