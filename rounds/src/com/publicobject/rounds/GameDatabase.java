/**
 * Copyright (C) 2012 Jesse Wilson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicobject.rounds;

import android.content.Context;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game data access. Most operations are blocking. May be used by multiple
 * threads concurrently.
 */
public final class GameDatabase {
    private static final Type PLAYERS_MAP_TYPE = new TypeToken<Map<String, Player>>() {}.getType();
    private static final int RECENT_GAME_THRESHOLD = 3;

    /** From most games played to least games played */
    private static final Comparator<Map.Entry<String, Player>> ORDER_BY_PLAY_COUNT
            = new Comparator<Map.Entry<String, Player>>() {
        @Override public int compare(Map.Entry<String, Player> a, Map.Entry<String, Player> b) {
            int aGames = a.getValue().totalGames;
            int bGames = b.getValue().totalGames;
            if (aGames == bGames) {
                return 0;
            } else {
                return aGames < bGames ? 1 : -1;
            }
        }
    };

    /** From most recently played to least recently played */
    private static final Comparator<Map.Entry<String, Player>> ORDER_BY_MOST_RECENTLY_PLAYED
            = new Comparator<Map.Entry<String, Player>>() {
        @Override public int compare(Map.Entry<String, Player> a, Map.Entry<String, Player> b) {
            long aMostRecent = a.getValue().mostRecentGame;
            long bMostRecent = b.getValue().mostRecentGame;
            if (aMostRecent == bMostRecent) {
                return 0;
            } else {
                return aMostRecent < bMostRecent ? 1 : -1;
            }
        }
    };

    private static final Comparator<File> ORDER_BY_NAME = new Comparator<File>() {
        @Override public int compare(File a, File b) {
            return a.getName().compareTo(b.getName());
        }
    };

    private static GameDatabase singleton;

    private final File gameDir;
    private Map<String, Player> players;

    public static synchronized GameDatabase getInstance(Context context) {
        if (singleton == null) {
            singleton = new GameDatabase(context);
        }
        return singleton;
    }

    public GameDatabase(File gameDir) {
        this.gameDir = gameDir;
    }

    private GameDatabase(Context context) {
        this.gameDir = context.getDir("gameData", Context.MODE_PRIVATE);
    }

    private File getFile(String name) {
        return new File(gameDir, name);
    }

    /**
     * Games are identified by their start date in hex. This way the most recent
     * game is always sorted to the end in an alphabetical sort.
     */
    private String createGameId(Game game) {
        if (game.getDateStarted() == 0) {
            throw new IllegalArgumentException();
        }
        return String.format("%016x", game.getDateStarted());
    }

    /**
     * Returns non-empty player name suggestions ordered by best suggestion.
     * This returns the names of the players from the 3 most recent games,
     * followed by all other players ordered by play count.
     */
    public synchronized Set<String> suggestedPlayerNames() {
        Map<String, Player> players = readPlayers();

        if (players.isEmpty()) {
            return Collections.emptySet();
        }

        List<Map.Entry<String, Player>> byMostRecentGame
                = new ArrayList<Map.Entry<String, Player>>(players.entrySet());
        Collections.sort(byMostRecentGame, ORDER_BY_MOST_RECENTLY_PLAYED);

        Set<String> result = new LinkedHashSet<String>();

        long lastDate = byMostRecentGame.get(0).getValue().mostRecentGame;
        int dateChanges = 0;
        for (Map.Entry<String, Player> entry : byMostRecentGame) {
            if (lastDate != entry.getValue().mostRecentGame) {
                lastDate = entry.getValue().mostRecentGame;
                dateChanges++;
            }

            if (dateChanges == RECENT_GAME_THRESHOLD) {
                break;
            }

            result.add(entry.getKey());
        }

        List<Map.Entry<String, Player>> byPlayCount
                = new ArrayList<Map.Entry<String, Player>>(players.entrySet());
        Collections.sort(byPlayCount, ORDER_BY_PLAY_COUNT);
        for (Map.Entry<String, Player> entry : byPlayCount) {
            result.add(entry.getKey());
        }

        return result;
    }

    private Map<String, Player> readPlayers() {
        if (players == null) {
            try {
                Reader reader = new InputStreamReader(new FileInputStream(
                        getFile("players.json")), "UTF-8");
                players = Json.gson.fromJson(reader, PLAYERS_MAP_TYPE);
            } catch (IOException e) {
                players = new LinkedHashMap<String, Player>();
            }
        }
        return players;
    }

    private void writePlayers() {
        File scratch = getFile("players.scratch");
        File saved = getFile("players.json");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(scratch), "UTF-8");
            Json.gson.toJson(players, PLAYERS_MAP_TYPE, writer);
            writer.close();
            rename(scratch, saved);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes game to persistent storage, updating a saved instance of the game
     * if it already exists.
     */
    public synchronized void save(Game game) {
        String id = game.getId();
        boolean isNewGame = false;
        if (id == null) {
            id = createGameId(game);
            game.setId(id);
            isNewGame = true;
        }

        // save the game to the filesystem
        File scratch = getFile(id + ".scratch");
        File saved = getFile(id + ".game");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(scratch), "UTF-8");
            Json.gson.toJson(game, Game.class, writer);
            writer.close();
            rename(scratch, saved);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // also update the player database if this is a new game
        if (isNewGame) {
            Map<String, Player> players = readPlayers();
            for (int p = 0; p < game.playerCount(); p++) {
                Player player = players.get(game.playerName(p));
                if (player == null) {
                    player = new Player();
                    players.put(game.playerName(p), player);
                }
                player.mostRecentColor = game.playerColor(p);
                player.mostRecentGame = game.getDateStarted();
                player.totalGames++;
            }
            writePlayers();
        }
    }

    private void rename(File from, File to) throws IOException {
        if (!from.renameTo(to)) {
            throw new IOException("Failed to rename " + from + " to " + to);
        }
    }

    private void delete(File file) {
        if (!file.delete()) {
            throw new RuntimeException("Failed to delete " + file);
        }
    }

    /**
     * Returns all games ordered from newest to oldest.
     */
    public List<Game> allGames() {
        try {
            List<Game> result = new ArrayList<Game>();
            for (File file : gameFilesByMostRecent()) {
                Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
                Game game = Json.gson.fromJson(reader, Game.class);
                result.add(game);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<File> gameFilesByMostRecent() {
        List<File> result = new ArrayList<File>();
        for (File file : gameDir.listFiles()) {
            if (file.getName().endsWith(".game")) {
                result.add(file);
            }
        }
        Collections.sort(result, ORDER_BY_NAME);
        Collections.reverse(result);
        return result;
    }

    /**
     * Removes {@code games} from persistent storage. Player records are not
     * modified.
     */
    public void deleteGames(Set<Game> games) {
        for (Game game : games) {
            delete(getFile(game.getId() + ".game"));
        }
    }

    /**
     * Returns the most recently played game, or null if no games have been
     * saved.
     */
    public Game mostRecentGame() {
        try {
            List<File> files = gameFilesByMostRecent();
            if (files.isEmpty()) {
                return null;
            }
            Reader reader = new InputStreamReader(new FileInputStream(files.get(0)), "UTF-8");
            return Json.gson.fromJson(reader, Game.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Player {
        int totalGames;
        long mostRecentGame;
        int mostRecentColor;
    }
}
