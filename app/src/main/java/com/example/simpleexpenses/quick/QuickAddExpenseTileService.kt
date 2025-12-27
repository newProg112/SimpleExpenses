package com.example.simpleexpenses.quick

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.simpleexpenses.MainActivity

@RequiresApi(Build.VERSION_CODES.N) // API 24+
class QuickAddExpenseTileService : TileService() {

    override fun onClick() {
        super.onClick()

        // Build an Intent to open MainActivity and tell it to go to "add expense"
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_add_camera", true)
        }

        // Collapse the shade and launch the activity
        startActivityAndCollapse(intent)
    }
}