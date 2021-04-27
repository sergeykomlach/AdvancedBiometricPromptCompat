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

import android.app.ProgressDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.myapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (secure) {
            //prevent screen capturing
//            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
//        } else {
//            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
//        }
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

            val scanTask = object : AsyncTask<Void, Void, String>() {
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
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).text = it
                        Toast.makeText(
                            this@MainActivity,
                            "Report copied to clipboard",
                            Toast.LENGTH_LONG
                        ).show()

                    }
                }
            }

            scanTask.execute()
        }
//        StatusBarTools.setNavBarAndStatusBarColors(window, Color.BLUE, Color.RED)
    }

    fun showDialog() {
        val dialogFragment = AppCompactBaseDialogFragment()
        dialogFragment.show(supportFragmentManager, null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}