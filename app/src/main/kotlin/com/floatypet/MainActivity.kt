package com.floatypet

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.floatypet.overlay.OverlayPermission
import com.floatypet.overlay.OverlayService
import com.floatypet.ui.adopt.EditRoute
import com.floatypet.ui.ai.AiConfigRoute
import com.floatypet.ui.home.HomeRoute
import com.floatypet.ui.home.HomeViewModel
import com.floatypet.ui.theme.FloatyPetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    // 选图回调：拿到 uri 后跳编辑页
    private var onImagePicked: ((Uri) -> Unit)? = null

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onImagePicked?.invoke(uri)
        }

    // 从悬浮窗权限设置页返回后，重新校验并尝试启动
    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (OverlayPermission.isGranted(this)) startOverlay()
            else Toast.makeText(this, "未开启悬浮窗权限，宠物无法显示", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatyPetTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeRoute(
                                onAdoptPet = {
                                    onImagePicked = { uri ->
                                        navController.navigate("edit/${Uri.encode(uri.toString())}")
                                    }
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                onToggleOverlay = ::toggleOverlay,
                                onGoAiConfig = { navController.navigate("ai_config") },
                                modifier = Modifier.padding(padding),
                            )
                        }
                        composable("edit/{uri}") { backStack ->
                            val uriStr = backStack.arguments?.getString("uri").orEmpty()
                            EditRoute(
                                imageUri = Uri.parse(Uri.decode(uriStr)),
                                onSaved = {
                                    reloadOverlayIfRunning()
                                    navController.popBackStack("home", inclusive = false)
                                },
                                onBack = { navController.popBackStack() },
                                onGoAiConfig = { navController.navigate("ai_config") },
                            )
                        }
                        composable("ai_config") {
                            AiConfigRoute(
                                onSaved = { navController.popBackStack() },
                            )
                        }
                    }
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
        if (OverlayPermission.isGranted(this)) startOverlay()
        else overlaySettingsLauncher.launch(OverlayPermission.createSettingsIntent(this))
    }

    private fun startOverlay() {
        OverlayService.start(this)
        homeViewModel.setOverlayRunning(true)
    }

    /** 换了宠物素材后，若悬浮窗在运行则重启以加载新帧。 */
    private fun reloadOverlayIfRunning() {
        if (OverlayService.isRunning) {
            OverlayService.stop(this)
            OverlayService.start(this)
        }
    }
}
