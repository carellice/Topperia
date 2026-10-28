import it.topperia.game.engine.GameSession;
import it.topperia.game.engine.RankedList;
import it.topperia.game.data.Rankings;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Standalone regression tests: javac + java, no emulator or test framework needed. */
public final class EngineTest {
    private static int assertions;

    public static void main(String[] args) {
        normalizationAndMatching();
        tenAttemptsAndDuplicateRules();
        perfectGame();
        independentDuelBoardsAndWinner();
        tiedDuel();
        saveAndRestoreEveryPhase();
        rejectsInvalidSavesAndRankings();
        shippedRankingsIntegration();
        System.out.println("PASS: " + assertions + " Topperia engine assertions");
    }

    private static RankedList ranking() {
        return new RankedList("test", "Una Top 10", "Prova", "Mondo", "world", "Fonte",
                "https://example.org/data", "2024", "Dati di prova",
                new RankedList.Entry("Stati Uniti", "100", "USA", "United States", "America"),
                new RankedList.Entry("Città del Vaticano", "90", "Vaticano"),
                new RankedList.Entry("Costa d'Avorio", "80"),
                new RankedList.Entry("Nuova Zelanda", "70", "New Zealand"),
                new RankedList.Entry("Italia", "60", "Italy"),
                new RankedList.Entry("Francia", "50", "France"),
                new RankedList.Entry("Germania", "40", "Germany"),
                new RankedList.Entry("Spagna", "30", "Spain"),
                new RankedList.Entry("Canada", "20"),
                new RankedList.Entry("Giappone", "10", "Japan"));
    }

    private static void normalizationAndMatching() {
        RankedList list = ranking();
        check(list.indexOf("  U.S.A.  ") == 0, "punctuation is ignored");
        check(list.indexOf("CITTA DEL VATICANO") == 1, "Italian accents and case are ignored");
        check(list.indexOf("Costa d’Avorio") == 2, "curly apostrophe is ignored");
        check(list.indexOf("nuova   zelanda") == 3, "extra spaces are ignored");
        check(list.indexOf("france") == 5, "explicit alias is accepted");
        check(list.indexOf("Francesca") == -1, "prefix matching cannot produce false hits");
        check(list.indexOf("U") == -1, "a partial answer is not accepted");
        check(list.indexOf(null) == -1, "null lookup is safe");
        check(RankedList.normalize("!?  ").isEmpty(), "punctuation-only input is blank");
    }

    private static void tenAttemptsAndDuplicateRules() {
        GameSession game = new GameSession(ranking(), GameSession.Mode.SOLO);
        check(game.submit("").status == GameSession.GuessStatus.EMPTY, "blank input is ignored");
        check(game.submit("! ").status == GameSession.GuessStatus.EMPTY, "punctuation is ignored");
        check(game.currentPlayer().attemptsRemaining() == 10, "empty guesses spend no attempt");
        check(game.submit("USA").status == GameSession.GuessStatus.HIT, "correct answer hits");
        check(game.submit("Stati Uniti").status == GameSession.GuessStatus.DUPLICATE, "alias of known answer is a duplicate");
        check(game.submit("Brasile").status == GameSession.GuessStatus.MISS, "unknown answer misses");
        check(game.submit(" BRÁSILE ").status == GameSession.GuessStatus.DUPLICATE, "wrong answer cannot be repeated");
        check(game.currentPlayer().attemptsRemaining() == 8, "only two unique guesses used");
        for (int i = 0; i < 8; i++) game.submit("sbagliata " + i);
        check(game.phase() == GameSession.Phase.FINISHED, "ten spent guesses end a solo game");
        check(game.currentPlayer().attemptsUsed() == 10, "exactly ten attempts counted");
        check(game.currentPlayer().score() == 1, "score counts only hits");
        check(game.submit("Italia").status == GameSession.GuessStatus.INACTIVE, "cannot guess after game ends");
        check(game.currentPlayer().score() == 1, "finished score cannot change");
        boolean immutable = false;
        try { game.currentPlayer().guesses().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "guess history cannot be externally mutated");
    }

    private static void perfectGame() {
        GameSession game = new GameSession(ranking(), GameSession.Mode.SOLO);
        for (RankedList.Entry entry : game.ranking.entries) game.submit(entry.name);
        check(game.phase() == GameSession.Phase.FINISHED, "all ten hits finish the game");
        check(game.currentPlayer().score() == 10, "perfect score is ten");
        for (int i = 0; i < 10; i++) check(game.currentPlayer().isRevealed(i), "every entry is revealed");
        check(!game.isDraw(), "solo game is never a duel draw");
    }

    private static void independentDuelBoardsAndWinner() {
        GameSession game = new GameSession(ranking(), GameSession.Mode.DUEL, "Ada", "Leo");
        check(!game.startSecondPlayer(), "handoff cannot start before player one finishes");
        game.submit("Italia");
        fillWrong(game, 9);
        check(game.phase() == GameSession.Phase.HANDOFF, "first ten guesses reach a private handoff");
        check(game.activePlayerIndex() == 0, "handoff retains player one until explicit continue");
        check(game.submit("Francia").status == GameSession.GuessStatus.INACTIVE, "handoff cannot accept guesses");
        check(game.player(1).score() == 0, "second player has no inherited score");
        check(game.startSecondPlayer(), "handoff can be acknowledged");
        check(game.activePlayerIndex() == 1, "player two now active");
        check(game.currentPlayer().attemptsRemaining() == 10, "second player has ten fresh attempts");
        check(!game.currentPlayer().isRevealed(4), "first player's reveal is hidden from second board");
        check(game.submit("Italia").status == GameSession.GuessStatus.HIT, "same answer is valid for second player");
        game.submit("Francia");
        fillWrong(game, 8);
        check(game.phase() == GameSession.Phase.FINISHED, "second ten guesses finish the duel");
        check(game.player(0).score() == 1 && game.player(1).score() == 2, "scores remain independent");
        check(game.winnerIndex() == 1, "higher score wins");
        check(!game.startSecondPlayer(), "handoff cannot restart a finished game");
    }

    private static void tiedDuel() {
        GameSession game = new GameSession(ranking(), GameSession.Mode.DUEL);
        fillWrong(game, 10);
        game.startSecondPlayer();
        fillWrong(game, 10);
        check(game.isDraw(), "equal final scores are a draw");
        check(game.winnerIndex() == -1, "draw has no winner");
    }

    private static void saveAndRestoreEveryPhase() {
        for (GameSession.Mode mode : GameSession.Mode.values()) {
            GameSession game = new GameSession(ranking(), mode, "Àda", "Léon");
            assertSame(game, GameSession.restore(ranking(), game.serialize()));
            game.submit("U.S.A.");
            game.submit("errore");
            assertSame(game, GameSession.restore(ranking(), game.serialize()));
            fillWrong(game, 8);
            assertSame(game, GameSession.restore(ranking(), game.serialize()));
            if (mode == GameSession.Mode.DUEL) {
                game.startSecondPlayer();
                assertSame(game, GameSession.restore(ranking(), game.serialize()));
                game.submit("Vaticano");
                assertSame(game, GameSession.restore(ranking(), game.serialize()));
                fillWrong(game, 9);
                assertSame(game, GameSession.restore(ranking(), game.serialize()));
            }
        }
    }

    private static void rejectsInvalidSavesAndRankings() {
        expectInvalid(() -> GameSession.restore(ranking(), "invalid!"), "corrupt saves are rejected");
        expectInvalid(() -> GameSession.restore(ranking(), ""), "truncated saves are rejected");
        expectInvalid(() -> new RankedList("test", "Titolo", "Sub", "Mondo", "world", "Fonte",
                "https://example.org", "2024", "Metodo"), "rankings must have ten entries");
        List<RankedList.Entry> ambiguousEntries = new ArrayList<>(ranking().entries);
        ambiguousEntries.set(1, new RankedList.Entry("Qualcosa", "90", "USA"));
        expectInvalid(() -> new RankedList("test", "Titolo", "Sub", "Mondo", "world", "Fonte",
                "https://example.org", "2024", "Metodo", ambiguousEntries.toArray(new RankedList.Entry[0])),
                "ambiguous answer aliases are rejected");
        String save = new GameSession(ranking(), GameSession.Mode.SOLO).serialize();
        List<RankedList.Entry> entries = new ArrayList<>(ranking().entries);
        entries.set(0, new RankedList.Entry("Altra risposta", "100"));
        RankedList changed = new RankedList("test", "Titolo", "Sub", "Mondo", "world", "Fonte",
                "https://example.org", "2024", "Metodo", entries.toArray(new RankedList.Entry[0]));
        expectInvalid(() -> GameSession.restore(changed, save), "changed datasets cannot silently alter saved scores");
    }

    private static void shippedRankingsIntegration() {
        check(Rankings.all().size() == 30, "all thirty editorial rankings ship");
        Set<String> ids = new HashSet<>();
        for (RankedList ranking : Rankings.all()) {
            check(ids.add(ranking.id), "each ranking has a unique persistence id");
            check(Rankings.byId(ranking.id) == ranking, "ranking lookup preserves its dataset");
            check(ranking.entries.size() == 10, "every shipped ranking contains ten entries");
            URI source = URI.create(ranking.sourceUrl);
            check("https".equals(source.getScheme()) && source.getHost() != null,
                    "every shipped ranking has an absolute HTTPS source");
            check(!ranking.sourceName.trim().isEmpty() && !ranking.referenceYear.trim().isEmpty()
                    && !ranking.methodology.trim().isEmpty(), "source name, reference date, and methodology are present");
            GameSession game = new GameSession(ranking, GameSession.Mode.SOLO);
            for (int i = 0; i < ranking.entries.size(); i++) {
                RankedList.Entry entry = ranking.entries.get(i);
                check(ranking.indexOf(entry.name) == i, "canonical shipped answer has its exact position");
                check(ranking.indexOf(entry.name.toUpperCase(java.util.Locale.ITALIAN)) == i,
                        "shipped answer accepts uppercase");
                for (String alias : entry.aliases) {
                    check(ranking.indexOf(alias) == i, "every shipped alias maps to the same position");
                }
                String answer = entry.aliases.isEmpty() ? entry.name : entry.aliases.get(entry.aliases.size() - 1);
                check(game.submit(answer).status == GameSession.GuessStatus.HIT,
                        "every shipped answer can be played using an accepted alias");
                if (i < 9) {
                    check(game.submit(entry.name).status == GameSession.GuessStatus.DUPLICATE,
                            "canonical name cannot spend an extra attempt after an alias");
                }
                check(game.currentPlayer().attemptsUsed() == i + 1, "alias duplicate does not spend attempts");
                game = GameSession.restore(ranking, game.serialize());
                check(game.currentPlayer().score() == i + 1, "shipped ranking restores its score after every answer");
            }
            check(game.phase() == GameSession.Phase.FINISHED && game.currentPlayer().score() == 10,
                    "every shipped ranking supports a perfect ten-answer game");
        }
        check(Rankings.byId("not-a-ranking") == null, "unknown ranking lookup is safe");
    }

    private static void fillWrong(GameSession game, int count) {
        for (int i = 0; i < count; i++) game.submit("errore nuovo " + i);
    }

    private static void assertSame(GameSession original, GameSession restored) {
        check(original.mode == restored.mode, "save preserves mode");
        check(original.phase() == restored.phase(), "save preserves phase");
        check(original.activePlayerIndex() == restored.activePlayerIndex(), "save preserves active player");
        for (int p = 0; p < original.playerCount(); p++) {
            check(original.player(p).name.equals(restored.player(p).name), "save preserves player name");
            check(original.player(p).score() == restored.player(p).score(), "save preserves score");
            check(original.player(p).attemptsUsed() == restored.player(p).attemptsUsed(), "save preserves spent attempts");
            for (int i = 0; i < 10; i++) {
                check(original.player(p).isRevealed(i) == restored.player(p).isRevealed(i), "save preserves board");
            }
        }
        check(original.serialize().equals(restored.serialize()), "save round-trip is deterministic");
    }

    private static void expectInvalid(Runnable runnable, String message) {
        boolean invalid = false;
        try { runnable.run(); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
