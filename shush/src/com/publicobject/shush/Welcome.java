/**
 * Copyright (C) 2009 Jesse Wilson
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

package com.publicobject.shush;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

/**
 * A dialog that explains how Shush works.
 */
public class Welcome extends Activity implements OnClickListener, OnCancelListener {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setIcon(R.drawable.shush)
                .setTitle("Shush! Ringer Restorer")
                .setMessage("Activate Shush by turning your ringer off. You can "
                        + "use the volume down button just like you're used to.")
                .setOnCancelListener(this)
                .setPositiveButton("Sweet!", this)
                .create()
                .show();
    }

    public void onClick(DialogInterface dialogInterface, int i) {
        finish();
    }

    public void onCancel(DialogInterface dialogInterface) {
        finish();
    }
}
