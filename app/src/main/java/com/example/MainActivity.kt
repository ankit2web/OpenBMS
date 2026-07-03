package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.BmsViewModel
import com.example.ui.BmsViewModelFactory
import com.example.ui.screens.BmsMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create our BMS ViewModel with the custom Application context factory
        val viewModel = ViewModelProvider(
            this, 
            BmsViewModelFactory(application)
        )[BmsViewModel::class.java]

        setContent {
            MyApplicationTheme {
                BmsMainScreen(viewModel = viewModel)
            }
        }
    }
}
