package it.topperia.game.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure game rules. A duel keeps the same ranking and gives each player a fresh board. */
public final class GameSession {
    public static final int MAX_ATTEMPTS = 10;
    public static final int MAX_INPUT_LENGTH = 120;
    private static final int SAVE_VERSION = 1;

    public enum Mode { SOLO, DUEL }
    public enum Phase { PLAYING, HANDOFF, FINISHED }
    public enum GuessStatus { HIT, MISS, DUPLICATE, EMPTY, INACTIVE }

    public final RankedList ranking;
    public final Mode mode;
    private final PlayerState[] players;
    private int activePlayer;
    private Phase phase = Phase.PLAYING;

    public GameSession(RankedList ranking, Mode mode) {
        this(ranking, mode, mode == Mode.SOLO ? "Tu" : "Giocatore 1", "Giocatore 2");
    }

    public GameSession(RankedList ranking, Mode mode, String playerOne, String playerTwo) {
        if (ranking == null || mode == null) throw new IllegalArgumentException("Ranking and mode are required");
        this.ranking = ranking;
        this.mode = mode;
        players = mode == Mode.SOLO
                ? new PlayerState[] {new PlayerState(playerOne)}
                : new PlayerState[] {new PlayerState(playerOne), new PlayerState(playerTwo)};
    }

    public Phase phase() { return phase; }
    public int activePlayerIndex() { return activePlayer; }
    public int playerCount() { return players.length; }
    public PlayerState currentPlayer() { return players[activePlayer]; }
    public PlayerState player(int index) { return players[index]; }

    public GuessResult submit(String input) {
        PlayerState player = currentPlayer();
        if (phase != Phase.PLAYING) return result(GuessStatus.INACTIVE, -1);
        String raw = input == null ? "" : input.trim();
        if (raw.length() > MAX_INPUT_LENGTH) raw = raw.substring(0, MAX_INPUT_LENGTH);
        String normalized = RankedList.normalize(raw);
        if (normalized.isEmpty()) return result(GuessStatus.EMPTY, -1);
        int index = ranking.indexOf(raw);
        if (player.normalizedGuesses.contains(normalized) || (index >= 0 && player.revealed[index])) {
            return result(GuessStatus.DUPLICATE, index);
        }
        GuessStatus status = index >= 0 ? GuessStatus.HIT : GuessStatus.MISS;
        player.normalizedGuesses.add(normalized);
        player.guesses.add(new Guess(raw, status, index));
        if (index >= 0) {
            player.revealed[index] = true;
            player.score++;
        }
        if (player.attemptsRemaining() == 0 || player.score == ranking.entries.size()) {
            phase = mode == Mode.DUEL && activePlayer == 0 ? Phase.HANDOFF : Phase.FINISHED;
        }
        return result(status, index);
    }

    private GuessResult result(GuessStatus status, int index) {
        return new GuessResult(status, index, currentPlayer().score(),
                currentPlayer().attemptsRemaining(), phase);
    }

    /** Called only after the device has been passed. Previous answers are not copied. */
    public boolean startSecondPlayer() {
        if (mode != Mode.DUEL || phase != Phase.HANDOFF || activePlayer != 0) return false;
        activePlayer = 1;
        phase = Phase.PLAYING;
        return true;
    }

    /** -1 means a draw, unfinished game, or solo mode. */
    public int winnerIndex() {
        if (mode != Mode.DUEL || phase != Phase.FINISHED || players[0].score == players[1].score) return -1;
        return players[0].score > players[1].score ? 0 : 1;
    }

    public boolean isDraw() {
        return mode == Mode.DUEL && phase == Phase.FINISHED && players[0].score == players[1].score;
    }

    /** A bounded, versioned textual save; restore replays guesses through the same rule engine. */
    public String serialize() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(SAVE_VERSION);
            out.writeUTF(ranking.id);
            out.writeUTF(rankingSignature(ranking));
            out.writeUTF(mode.name());
            out.writeInt(activePlayer);
            out.writeUTF(phase.name());
            for (PlayerState player : players) {
                out.writeUTF(player.name);
                out.writeInt(player.guesses.size());
                for (Guess guess : player.guesses) out.writeUTF(guess.input);
            }
            out.flush();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException impossibleForMemoryStream) {
            throw new IllegalStateException(impossibleForMemoryStream);
        }
    }

    public static GameSession restore(RankedList ranking, String save) {
        if (ranking == null || save == null || save.length() > 20000) {
            throw new IllegalArgumentException("Invalid saved game");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(save)));
            if (in.readInt() != SAVE_VERSION || !ranking.id.equals(in.readUTF())
                    || !rankingSignature(ranking).equals(in.readUTF())) {
                throw new IllegalArgumentException("Saved game uses a different ranking or version");
            }
            Mode mode = Mode.valueOf(in.readUTF());
            int activePlayer = in.readInt();
            Phase savedPhase = Phase.valueOf(in.readUTF());
            int playerCount = mode == Mode.SOLO ? 1 : 2;
            String[] names = new String[playerCount];
            List<List<String>> guesses = new ArrayList<>();
            for (int p = 0; p < playerCount; p++) {
                names[p] = in.readUTF();
                int count = in.readInt();
                if (count < 0 || count > MAX_ATTEMPTS) throw new IllegalArgumentException("Invalid attempt count");
                List<String> inputs = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String input = in.readUTF();
                    if (input.length() > MAX_INPUT_LENGTH) throw new IllegalArgumentException("Saved answer too long");
                    inputs.add(input);
                }
                guesses.add(inputs);
            }
            if (in.available() != 0 || activePlayer < 0 || activePlayer >= playerCount) {
                throw new IllegalArgumentException("Invalid saved state");
            }
            GameSession session = new GameSession(ranking, mode, names[0], playerCount == 2 ? names[1] : "Giocatore 2");
            for (int p = 0; p < playerCount; p++) {
                if (p == 1) {
                    if (activePlayer == 1) {
                        if (!session.startSecondPlayer()) throw new IllegalArgumentException("Invalid handoff");
                    } else if (!guesses.get(p).isEmpty()) {
                        throw new IllegalArgumentException("Second player has not started");
                    }
                }
                for (String input : guesses.get(p)) {
                    GuessResult result = session.submit(input);
                    if (result.status != GuessStatus.HIT && result.status != GuessStatus.MISS) {
                        throw new IllegalArgumentException("Invalid saved guess");
                    }
                }
            }
            if (session.activePlayer != activePlayer || session.phase != savedPhase) {
                throw new IllegalArgumentException("Saved phase does not match attempts");
            }
            return session;
        } catch (IOException | IllegalArgumentException invalidSave) {
            throw new IllegalArgumentException("Could not restore game", invalidSave);
        }
    }

    private static String rankingSignature(RankedList ranking) {
        StringBuilder signature = new StringBuilder(ranking.referenceYear);
        for (RankedList.Entry entry : ranking.entries) {
            signature.append('\u001f').append(entry.name).append('\u001f').append(entry.value);
            for (String alias : entry.aliases) signature.append('\u001e').append(alias);
        }
        return Integer.toHexString(signature.toString().hashCode());
    }

    public static final class PlayerState {
        public final String name;
        private int score;
        private final boolean[] revealed = new boolean[MAX_ATTEMPTS];
        private final List<Guess> guesses = new ArrayList<>();
        private final Set<String> normalizedGuesses = new HashSet<>();

        private PlayerState(String name) {
            String trimmed = RankedList.requireText(name, "player name").trim();
            this.name = trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
        }

        public int score() { return score; }
        public int attemptsUsed() { return guesses.size(); }
        public int attemptsRemaining() { return MAX_ATTEMPTS - guesses.size(); }
        public boolean isRevealed(int entryIndex) { return revealed[entryIndex]; }
        public List<Guess> guesses() { return Collections.unmodifiableList(guesses); }
    }

    public static final class Guess {
        public final String input;
        public final GuessStatus status;
        public final int entryIndex;

        private Guess(String input, GuessStatus status, int entryIndex) {
            this.input = input;
            this.status = status;
            this.entryIndex = entryIndex;
        }
    }

    public static final class GuessResult {
        public final GuessStatus status;
        public final int entryIndex;
        public final int score;
        public final int attemptsRemaining;
        public final Phase phase;

        private GuessResult(GuessStatus status, int entryIndex, int score, int attemptsRemaining, Phase phase) {
            this.status = status;
            this.entryIndex = entryIndex;
            this.score = score;
            this.attemptsRemaining = attemptsRemaining;
            this.phase = phase;
        }
    }
}
