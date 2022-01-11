/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.example.myapplication

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.devtools.LogCat
import com.example.myapplication.devtools.Scan4Apis
import com.example.myapplication.utils.MailTo
import dev.skomlach.biometric.compat.utils.statusbar.StatusBarTools
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.storage.SharedPreferenceProvider

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val controller: WindowInsetsControllerCompat by lazy {
        WindowInsetsControllerCompat(window, binding.root)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { _ ->
            val dialog = ProgressDialog.show(
                this@MainActivity, "Looking for API's",
                "Please wait...", true
            )

            val scanTask = @SuppressLint("StaticFieldLeak")
            object : AsyncTask<Void, Void, String>() {
                override fun onPreExecute() {
                    dialog.show()
                }

                override fun doInBackground(vararg params: Void?): String? {
                    return Scan4Apis(this@MainActivity).getList()
                }

                override fun onPostExecute(result: String?) {
                    dialog.dismiss()
                    if (result?.isNullOrEmpty() == true) {
                        Toast.makeText(
                            this@MainActivity,
                            "Unexpected error happens",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    result.let {
                        MailTo.startMailClient(
                            this@MainActivity,
                            "s.komlach@gmail.com",
                            "Advanced BiometricPromptCompat Report",
                            it
                        )

                    }
                }
            }

            scanTask.execute()
        }
        val color = if (DarkLightThemes.isNightMode(this)) Color.WHITE else Color.BLACK
        StatusBarTools.setNavBarAndStatusBarColors(window, color, Color.TRANSPARENT, color)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            updateUI()
    }


    fun updateUI() {
        if (SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("checkboxFullscreen", false)
        )
            hideSystemUI()
        else
            showSystemUI()

        if (SharedPreferenceProvider.getPreferences("app_settings")
                .getBoolean("checkboxWindowSecure", false)
        ) {
//        prevent screen capturing
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }


    }

    fun sendLogs() {
        LogCat.setLog2ViewCallback(object : LogCat.Log2ViewCallback {
            override fun log(log: String) {
                LogCat.setLog2ViewCallback(null)
                MailTo.startMailClient(
                    this@MainActivity,
                    "s.komlach@gmail.com",
                    "Advanced BiometricPromptCompat Logs",
                    log
                )
            }
        })

    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun showDialog() {
        val dialogFragment = AppCompactBaseDialogFragment()
        dialogFragment.show(supportFragmentManager, null)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}