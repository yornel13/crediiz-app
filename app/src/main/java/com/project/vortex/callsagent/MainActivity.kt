package com.project.vortex.callsagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.project.vortex.callsagent.presentation.navigation.AppNavGraph
import com.project.vortex.callsagent.ui.theme.CallsAgendsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallsAgendsTheme {
                AppNavGraph()
            }
        }
    }
}
