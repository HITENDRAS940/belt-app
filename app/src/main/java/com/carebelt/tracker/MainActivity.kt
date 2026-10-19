package com.carebelt.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carebelt.tracker.ui.BeltTrackerScreen
import com.carebelt.tracker.ui.BeltTrackerViewModel
import com.carebelt.tracker.ui.theme.CareBeltTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CareBeltTheme {
                val trackerViewModel: BeltTrackerViewModel = viewModel()
                BeltTrackerScreen(viewModel = trackerViewModel)
            }
        }
    }
}
