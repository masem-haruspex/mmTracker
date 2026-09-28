package com.mtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mtracker.ui.MTrackerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val summaryDate = intent.getStringExtra("show_summary_date")
        setContent {
            MTrackerApp(initialSummaryDate = summaryDate)
        }
    }
}
