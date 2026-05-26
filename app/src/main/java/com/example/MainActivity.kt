package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RootCheckerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: RootCheckerViewModel = viewModel()
      // Initialize preferences
      viewModel.initSettings(applicationContext)
      val state by viewModel.state.collectAsState()

      MyApplicationTheme(darkTheme = state.isDarkTheme) {
        RootCheckerScreen(viewModel = viewModel)
      }
    }
  }
}
