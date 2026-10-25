package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.ui.AuraViewModel
import com.example.aura.ui.AuraWorkspaceScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val auraViewModel: AuraViewModel = viewModel(
                    factory = AuraViewModel.provideFactory(applicationContext)
                )
                AuraWorkspaceScreen(viewModel = auraViewModel)
            }
        }
    }
}
