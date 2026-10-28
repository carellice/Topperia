import it.topperia.game.engine.RankedList;
import it.topperia.game.engine.RankingPicker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Standalone behavioral checks; no Android runtime is required. */
public final class RankingPickerTest {
    private static int checks;
    public static void main(String[] args) {
        List<RankedList> all = Arrays.asList(ranking("a"), ranking("b"), ranking("c"), ranking("d"), ranking("e"));
        cyclesWithoutRepeats(all);
        unseenComeFirst(all);
        poolsAreIndependent(all);
        orderingDoesNotResetBag(all);
        seededSequencesMatch(all);
        directSelectionsDoNotRepeat(all);
        invalidInputs(all);
        System.out.println("RankingPickerTest: PASS " + checks + " assertions");
    }

    private static void cyclesWithoutRepeats(List<RankedList> all) {
        RankingPicker picker = new RankingPicker(new Random(47));
        String last = null;
        Set<String> played = new HashSet<>();
        for (RankedList ranking : all) played.add(ranking.id);
        for (int cycle = 0; cycle < 60; cycle++) {
            Set<String> picked = new HashSet<>();
            for (int i = 0; i < all.size(); i++) {
                String id = picker.pick(all, played, last).id;
                check(!id.equals(last), "avoid the previous ranking including cycle boundaries");
                check(picked.add(id), "draw every ranking once before refilling");
                last = id;
            }
            check(picked.size() == all.size(), "cycle covers the entire pool");
        }
    }

    private static void unseenComeFirst(List<RankedList> all) {
        RankingPicker picker = new RankingPicker(new Random(1));
        Set<String> played = new HashSet<>(Arrays.asList("a", "c", "e"));
        Set<String> first = new HashSet<>();
        String last = null;
        for (int i = 0; i < 2; i++) {
            String id = picker.pick(all, played, last).id;
            check(!played.contains(id), "unfinished rankings are preferred");
            first.add(id); last = id;
        }
        check(first.equals(new HashSet<>(Arrays.asList("b", "d"))), "unseen answers do not repeat without completion");
        Set<String> rest = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String id = picker.pick(all, played, last).id;
            rest.add(id); last = id;
        }
        check(rest.equals(played), "completed rankings still cycle after unseen draws");
    }

    private static void poolsAreIndependent(List<RankedList> all) {
        RankingPicker picker = new RankingPicker(new Random(3));
        List<RankedList> category = all.subList(0, 2);
        String first = picker.pick(category, Collections.<String>emptySet(), null).id;
        String other = picker.pick(category, Collections.<String>emptySet(), first).id;
        check(!first.equals(other), "a filtered pool draws its own candidates");
        for (int i = 0; i < 20; i++) {
            String id = picker.pick(category, null, other).id;
            check(id.equals("a") || id.equals("b"), "filtered choice never escapes its category");
            check(!id.equals(other), "two-entry category alternates across cycles");
            other = id;
        }
        List<RankedList> different = all.subList(2, 5);
        Set<String> picked = new HashSet<>();
        String last = null;
        for (int i = 0; i < different.size(); i++) {
            RankedList result = picker.pick(different, null, last);
            check(different.contains(result), "a second category uses only its candidates");
            picked.add(result.id); last = result.id;
        }
        check(picked.size() == different.size(), "a second category has its own bag");
    }

    private static void orderingDoesNotResetBag(List<RankedList> all) {
        RankingPicker picker = new RankingPicker(new Random(15));
        List<RankedList> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        Set<String> picked = new HashSet<>(); String last = null;
        for (int i = 0; i < all.size(); i++) {
            String id = picker.pick(i % 2 == 0 ? all : reversed, null, last).id;
            check(picked.add(id), "reordering the same candidates does not reset the bag");
            last = id;
        }
    }

    private static void seededSequencesMatch(List<RankedList> all) {
        RankingPicker one = new RankingPicker(new Random(912));
        RankingPicker two = new RankingPicker(new Random(912));
        String last = null;
        for (int i = 0; i < 100; i++) {
            String first = one.pick(all, null, last).id;
            check(first.equals(two.pick(all, null, last).id), "injected Random reproduces the same choices");
            last = first;
        }
    }

    private static void directSelectionsDoNotRepeat(List<RankedList> all) {
        RankingPicker picker = new RankingPicker(new Random(2));
        check(!picker.pick(all, null, "a").id.equals("a"), "last direct selection is avoided on first draw");
        for (int i = 0; i < 100; i++) {
            String external = all.get(i % all.size()).id;
            check(!picker.pick(all, null, external).id.equals(external), "external selections are reconciled with the bag");
        }
        RankedList single = all.get(0);
        for (int i = 0; i < 5; i++) {
            check(picker.pick(Collections.singletonList(single), null, single.id) == single, "a sole candidate remains playable");
        }
    }

    private static void invalidInputs(List<RankedList> all) {
        throwsArgument(() -> new RankingPicker(null));
        RankingPicker picker = new RankingPicker(new Random(7));
        throwsArgument(() -> picker.pick(null, null, null));
        throwsArgument(() -> picker.pick(Collections.<RankedList>emptyList(), null, null));
        throwsArgument(() -> picker.pick(Arrays.asList(all.get(0), null), null, null));
        throwsArgument(() -> picker.pick(Arrays.asList(all.get(0), all.get(0)), null, null));
    }

    private static RankedList ranking(String id) {
        RankedList.Entry[] entries = new RankedList.Entry[10];
        for (int i = 0; i < entries.length; i++) entries[i] = new RankedList.Entry("Entry " + i, "Value " + i);
        return new RankedList(id, "Title " + id, "Subtitle", "Category", "globe", "Source", "https://example.org", "2026", "Metric", entries);
    }

    private static void throwsArgument(Runnable action) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown, "invalid picker input is rejected clearly");
    }

    private static void check(boolean okay, String message) {
        checks++;
        if (!okay) throw new AssertionError(message);
    }
}
