package com.willi.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.willi.app.data.database.WilliDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.parseColor("#050005")

        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(2500)
            checkAndRoute()
        }
    }

    private suspend fun checkAndRoute() {
        val db = WilliApplication.instance.database
        val creator = db.creatorDao().get()

        if (creator == null || creator.name.isBlank()) {
            // Première fois → configuration
            startActivity(Intent(this, SetupActivity::class.java))
        } else {
            // Déjà configuré → interface principale
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
