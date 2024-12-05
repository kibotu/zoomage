/**
 * Copyright 2016 Jeffrey Sibbold
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
package com.jsibbold.zoomage.example

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jsibbold.zoomage.ZoomageView

class MainActivity : AppCompatActivity(), View.OnClickListener,
    CompoundButton.OnCheckedChangeListener {
    private var demoView: ZoomageView? = null
        get() = findViewById<ZoomageView>(R.id.demoView)
    private var optionsView: View? = null
    private var optionsDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prepareOptions()
    }

    private fun prepareOptions() {
        optionsView = layoutInflater.inflate(R.layout.zoomage_options, null)
        setSwitch(R.id.zoomable, demoView!!.isZoomable())
        setSwitch(R.id.translatable, demoView!!.isTranslatable())
        setSwitch(R.id.animateOnReset, demoView!!.getAnimateOnReset())
        setSwitch(R.id.autoCenter, demoView!!.getAutoCenter())
        setSwitch(R.id.restrictBounds, demoView!!.getRestrictBounds())
        optionsView!!.findViewById<View?>(R.id.reset).setOnClickListener(this)
        optionsView!!.findViewById<View?>(R.id.autoReset).setOnClickListener(this)

        optionsDialog = AlertDialog.Builder(this).setTitle("Zoomage Options")
            .setView(optionsView)
            .setPositiveButton("Close", null)
            .create()
    }

    private fun setSwitch(id: Int, state: Boolean) {
        val switchView = optionsView!!.findViewById<SwitchCompat>(id)
        switchView.setOnCheckedChangeListener(this)
        switchView.setChecked(state)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!optionsDialog!!.isShowing) {
            optionsDialog!!.show()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val id = buttonView.id
        if (id == R.id.zoomable) {
            demoView?.setZoomable(isChecked)
        } else if (id == R.id.translatable) {
            demoView?.setTranslatable(isChecked)
        } else if (id == R.id.restrictBounds) {
            demoView?.setRestrictBounds(isChecked)
        } else if (id == R.id.animateOnReset) {
            demoView?.setAnimateOnReset(isChecked)
        } else if (id == R.id.autoCenter) {
            demoView?.setAutoCenter(isChecked)
        }
    }

    override fun onClick(v: View) {
        if (v.id == R.id.reset) {
            demoView?.reset()
        } else {
            showResetOptions()
        }
    }

    private fun showResetOptions() {
        val options: Array<CharSequence> = arrayOf<CharSequence>("Under", "Over", "Always", "Never")

        val builder = AlertDialog.Builder(this)

        builder.setItems(options, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                demoView?.setAutoResetMode(which)
            }
        })

        builder.create().show()
    }
}
