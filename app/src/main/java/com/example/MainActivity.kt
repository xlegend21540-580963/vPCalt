package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.aistudio.vortexpc.input.InputDispatcher
import com.aistudio.vortexpc.ui.VortexAppShell
import com.example.service.ContainerSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject lateinit var inputDispatcher: InputDispatcher

  private var shortcutGameId by mutableStateOf<String?>(null)

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { /* Service gracefully operates regardless of notification toggle */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    shortcutGameId = intent?.getStringExtra("extra_game_id")

    setContent {
      VortexAppShell(
        shortcutGameId = shortcutGameId,
        onStartForegroundService = { title, container ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
          ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          ContainerSessionService.start(this, title, container)
        },
        onStopForegroundService = {
          ContainerSessionService.stop(this)
        },
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    shortcutGameId = intent.getStringExtra("extra_game_id")
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (inputDispatcher.handleHardwareKeyEvent(event)) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
    if (inputDispatcher.handleHardwareMotionEvent(ev)) {
      return true
    }
    return super.dispatchGenericMotionEvent(ev)
  }
}
