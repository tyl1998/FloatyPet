package com.floatypet

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.floatypet.overlay.OverlayPermission
import com.floatypet.overlay.OverlayService
import com.floatypet.ui.home.HomeRoute
import com.floatypet.ui.home.HomeViewModel
import com.floatypet.ui.theme.FloatyPetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    // 从悬浮窗权限设置页返回后，重新校验并尝试启动
    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (OverlayPermission.isGranted(this)) {
                startOverlay()
            } else {
                Toast.makeText(this, "未开启悬浮窗权限，宠物无法显示", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatyPetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    HomeRoute(
                        onToggleOverlay = ::toggleOverlay,
                        onAdoptPet = { /* TODO: 跳转素材导入流程 */ },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            OverlayService.stop(this)
            homeViewModel.setOverlayRunning(false)
            return
        }
        if (OverlayPermission.isGranted(this)) {
            startOverlay()
        } else {
            // 跳系统设置页授权（Android 无运行时弹窗）
            overlaySettingsLauncher.launch(OverlayPermission.createSettingsIntent(this))
        }
    }

    private fun startOverlay() {
        OverlayService.start(this)
        homeViewModel.setOverlayRunning(true)
    }
}
