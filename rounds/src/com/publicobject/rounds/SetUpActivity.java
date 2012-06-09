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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Set;

public final class SetUpActivity extends Activity {
    private AutoCompleteTextView name;
    private TextView names;
    private Button next;

    private Game game;
    private int editingPlayer;
    private ColorPicker colorPicker;
    private MenuItem play;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout layout = (FrameLayout) getLayoutInflater().inflate(R.layout.set_up, null);
        setContentView(layout);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("Add Players");

        Set<String> playerNames = GameDatabase.getInstance(getApplicationContext())
                .suggestedPlayerNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line,
                playerNames.toArray(new String[playerNames.size()]));
        name = (AutoCompleteTextView) layout.findViewById(R.id.name);
        name.setThreshold(1);
        name.setAdapter(adapter);
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence text, int a, int b, int c) {
                String currentName = name.getText().toString().trim();
                game.setPlayerName(editingPlayer, currentName);
                updateButtons();
                updateNameList();
            }

            @Override
            public void afterTextChanged(Editable textView) {
            }
        });
        name.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int action, KeyEvent event) {
                next();
                return true;
            }
        });

        View colorPlaceholder = layout.findViewById(R.id.colorPlaceholder);
        ColorPicker.Listener listener = new ColorPicker.Listener() {
            @Override public void selecting() {
                name.setVisibility(View.INVISIBLE);
                next.setVisibility(View.INVISIBLE);
                names.setVisibility(View.INVISIBLE);
            }
            @Override public void selected(int color) {
                game.setPlayerColor(editingPlayer, color);
                name.setVisibility(View.VISIBLE);
                next.setVisibility(View.VISIBLE);
                names.setVisibility(View.VISIBLE);
                updateNameList();
            }
        };
        colorPicker = new ColorPicker(getApplicationContext(), null, colorPlaceholder, listener);
        layout.addView(colorPicker);

        next = (Button) layout.findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                next();
            }
        });

        names = (TextView) layout.findViewById(R.id.names);
        names.setMovementMethod(LinkMovementMethod.getInstance());

        game = new Game();
        game.setDateStarted(System.currentTimeMillis());
        game.addPlayer("", pickAColor());

        editingPlayer = 0;
        editingPlayerChanged();
        updateNameList();
    }

    private void next() {
        if (!next.isEnabled()) {
            return; // ignore calls from the soft keyboard when the button is disabled
        }

        // if the current player doesn't have a name, remove it
        if (game.playerName(editingPlayer).isEmpty()) {
            game.removePlayer(editingPlayer);
            if (editingPlayer == game.playerCount()) {
                editingPlayer = 0; // next on the last empty player cycles to the first player
            }
        } else {
            editingPlayer++;
        }
        if (editingPlayer == 8) {
            editingPlayer = 0;
        }
        if (editingPlayer == game.playerCount()) {
            game.addPlayer("", pickAColor());
        }
        editingPlayerChanged();
    }

    private int pickAColor() {
        eachColor:
        for (int color : Colors.DEFAULT_COLORS) {
            for (int p = 0; p < game.playerCount(); p++) {
                if (color == game.playerColor(p)) {
                    continue eachColor;
                }
            }
            return color;
        }
        throw new AssertionError();
    }

    private void play() {
        if (nonEmptyPlayerCount() == 0) {
            throw new IllegalStateException();
        }
        if (game.playerName(editingPlayer).isEmpty()) {
            game.removePlayer(editingPlayer);
        }
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_GAME, Json.gameToJson(game));
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
        finish();
    }

    private void editingPlayerChanged() {
        String nameString = game.playerName(editingPlayer);
        name.setText(nameString);
        name.setSelection(nameString.length());
        colorPicker.setColor(game.playerColor(editingPlayer));
        updateButtons();
    }

    private void updateButtons() {
        int nonEmptyPlayerCount = nonEmptyPlayerCount();
        next.setEnabled(nonEmptyPlayerCount != 0);
        if (play != null) {
            play.setEnabled(nonEmptyPlayerCount != 0);
        }
    }

    private ClickableSpan newClickableSpan(final int player) {
        return new ClickableSpan() {
            @Override public void onClick(View widget) {
                if (player >= game.playerCount() || player == editingPlayer) {
                    return;
                }

                // if the current player doesn't have a name, remove that player
                if (game.playerName(editingPlayer).isEmpty()) {
                    game.removePlayer(editingPlayer);
                }

                editingPlayer = player;
                editingPlayerChanged();
            }
            @Override public void updateDrawState(TextPaint ds) {
            }
        };
    }

    private void updateNameList() {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int p = 0; p < game.playerCount(); p++) {
            String name = game.playerName(p);
            if (name.isEmpty()) {
                continue;
            }
            if (ssb.length() > 0) {
                ssb.append("  ");
            }
            ssb.append(name);
            ssb.setSpan(newClickableSpan(p), ssb.length() - name.length(), ssb.length(), 0);
            ssb.setSpan(new ForegroundColorSpan(game.playerColor(p)),
                    ssb.length() - name.length(), ssb.length(), 0);
        }
        names.setText(ssb);
    }

    private int nonEmptyPlayerCount() {
        // the only player that can have an empty name is the currently edited player
        int count = game.playerCount();
        if (game.playerName(editingPlayer).isEmpty()) {
            count--;
        }
        return count;
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.set_up, menu);
        play = menu.findItem(R.id.play);
        updateButtons();
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.play:
            play();
            return true;

        case android.R.id.home:
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right);
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
