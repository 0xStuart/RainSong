package com.stuartjohnson.rainsong

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.stuartjohnson.rainsong.ui.PlayScreen
import com.stuartjohnson.rainsong.ui.RainSongViewModel
import com.stuartjohnson.rainsong.ui.ScanScreen
import com.stuartjohnson.rainsong.ui.Screen
import com.stuartjohnson.rainsong.ui.theme.RainSongTheme

class MainActivity : ComponentActivity() {
    private val vm: RainSongViewModel by viewModels()
    private var pendingConnect: (() -> Unit)? = null

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingConnect?.invoke()
        pendingConnect = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        when {
            intent?.getBooleanExtra(EXTRA_DEMO_CLOUDS, false) == true -> {
                withNotification { vm.startCloudDemo() }
            }
            intent?.getBooleanExtra(EXTRA_DEMO_TCP, false) == true -> {
                withNotification { vm.connectTcp() }
            }
        }
        setContent {
            RainSongTheme {
                val state by vm.state.collectAsState()
                when (state.screen) {
                    Screen.Scan -> Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        ScanScreen(
                            state = state,
                            onScan = vm::startScan,
                            onConnectBle = { withNotification { vm.connectBle(it) } },
                            onAiUrl = vm::setAiUrl,
                            onDropVolume = vm::setDropVolume,
                            onMusicVolume = vm::setMusicVolume,
                            onCloudDemo = { withNotification { vm.startCloudDemo() } },
                        )
                    }
                    Screen.Play -> Surface(modifier = Modifier.fillMaxSize()) {
                        PlayScreen(
                            state = state,
                            onCloudTap = vm::simulateCloudDrop,
                            onDropVolume = vm::setDropVolume,
                            onMusicVolume = vm::setMusicVolume,
                            onDisconnect = vm::disconnectToScan,
                        )
                    }
                }
            }
        }
    }

    private fun withNotification(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingConnect = block
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            block()
        }
    }

    companion object {
        /** Emulator / harness: launch with `--ez demo_tcp true` to open TCP play. */
        const val EXTRA_DEMO_TCP = "demo_tcp"
        /** Skip sensor connect — open the cloud play screen only. */
        const val EXTRA_DEMO_CLOUDS = "demo_clouds"
    }
}
