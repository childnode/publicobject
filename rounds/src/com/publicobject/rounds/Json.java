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

import com.google.gson.Gson;

/**
 * Converts games to and from JSON.
 */
public final class Json {
    static final Gson gson = new Gson();

    public static Game jsonToGame(String json) {
        return gson.fromJson(json, Game.class);
    }

    public static String gameToJson(Game game) {
        return gson.toJson(game);
    }
}
