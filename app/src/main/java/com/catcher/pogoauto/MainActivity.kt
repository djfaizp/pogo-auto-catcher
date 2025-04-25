package com.catcher.pogoauto

import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.catcher.pogoauto.databinding.ActivityMainBinding
import java.io.DataOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            try {
                System.loadLibrary("frida-gadget")
            } catch (e: UnsatisfiedLinkError) {
                // Handle error: Frida gadget not found or couldn't be loaded
                // Log.e("MainActivity", "Failed to load frida-gadget", e)
            }
        }
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRootPermission()

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun requestRootPermission() {
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                // Root access granted
                Snackbar.make(binding.root, "Root access granted", Snackbar.LENGTH_SHORT).show()
            } else {
                // Root access denied or error
                Snackbar.make(binding.root, "Root access denied", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            // Error executing su
            Snackbar.make(binding.root, "Error requesting root: ${e.message}", Snackbar.LENGTH_LONG).show()
        } catch (e: InterruptedException) {
            // Error waiting for process
            Snackbar.make(binding.root, "Error requesting root: ${e.message}", Snackbar.LENGTH_LONG).show()
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (e: IOException) {
                // Ignore close error
            }
        }
    }
}