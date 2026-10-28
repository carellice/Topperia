package it.topperia.game;

import android.content.Context;
import android.content.SharedPreferences;
import it.topperia.game.engine.GameSession;

final class GameStore {
    final SharedPreferences prefs;
    GameStore(Context context) { prefs = context.getSharedPreferences("topperia", Context.MODE_PRIVATE); }
    int number(String key) { return prefs.getInt(key, 0); }
    boolean option(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    void toggle(String key, boolean fallback) { prefs.edit().putBoolean(key, !option(key, fallback)).apply(); }
    void record(GameSession session) {
        if (session.phase() != GameSession.Phase.FINISHED) return;
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt("rounds", number("rounds") + 1);
        int hits = 0, attempts = 0, best = number("best");
        for (int i = 0; i < session.playerCount(); i++) {
            hits += session.player(i).score();
            attempts += session.player(i).attemptsUsed();
            best = Math.max(best, session.player(i).score());
        }
        edit.putInt("hits", number("hits") + hits).putInt("attempts", number("attempts") + attempts);
        edit.putInt("best", best).putInt("duels", number("duels") + (session.mode == GameSession.Mode.DUEL ? 1 : 0));
        edit.putBoolean("played_" + session.ranking.id, true);
        edit.putString("session", session.serialize()).putString("ranking", session.ranking.id).putBoolean("recorded", true).apply();
    }
}
