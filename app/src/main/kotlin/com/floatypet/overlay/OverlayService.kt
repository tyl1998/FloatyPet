package com.floatypet.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.floatypet.MainActivity
import com.floatypet.R
import com.floatypet.core.model.PetAssetType
import com.floatypet.data.PetRepository
import com.floatypet.overlay.behavior.PetBehaviorEngine
import com.floatypet.overlay.behavior.PetMotionEngine
import com.floatypet.overlay.render.BubbleLayer
import com.floatypet.overlay.render.NoOpBubbleLayer
import com.floatypet.overlay.render.PetRenderer
import com.floatypet.overlay.render.SpriteRenderer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 悬浮窗前台服务。
 *
 * 进程铁律（CLAUDE.md §7）：不自启、不关联唤醒；仅用户主动开启时启动；
 * 前台服务 + LOW 通知保活，通知可隐藏；息屏停止渲染循环。
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var petRepository: PetRepository

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: PetOverlayView? = null

    private val renderer: PetRenderer = SpriteRenderer()
    private val bubbleLayer: BubbleLayer = NoOpBubbleLayer()
    private val behaviorEngine = PetBehaviorEngine()
    private val motionEngine = PetMotionEngine()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val choreographer = Choreographer.getInstance()
    private var looping = false

    private var petLoaded = false
    private var lastAction = com.floatypet.core.model.PetAction.IDLE
    private var walkDistancePx = 0f
    private var lastWalkX = Float.NaN

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopLoop()
                Intent.ACTION_SCREEN_ON -> if (petLoaded) startLoop()
            }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // 1. 运动引擎推进一帧
            val frame = motionEngine.tick()

            // 2. 更新窗口位置（运动驱动，不再手动拖拽时由引擎控制）
            if (motionEngine.state != PetMotionEngine.MoveState.DRAGGED) {
                layoutParams.x = frame.x.toInt().coerceIn(0, motionEngine.screenW - motionEngine.petSize)
                layoutParams.y = frame.y.toInt().coerceIn(0, motionEngine.screenH - motionEngine.petSize)
                overlayView?.let { runCatching { windowManager.updateViewLayout(it, layoutParams) } }
            }

            // 3. 通知渲染器动作 + 朝向（action 变化时才重置帧序列）
            if (frame.action != lastAction) {
                renderer.playAction(frame.action)
                lastAction = frame.action
            }
            // 步频与实际位移同步：位移越快，步子越快
            if (frame.action == com.floatypet.core.model.PetAction.WALK) {
                if (!lastWalkX.isNaN()) walkDistancePx += kotlin.math.abs(frame.x - lastWalkX)
                lastWalkX = frame.x
            } else {
                lastWalkX = Float.NaN
                walkDistancePx = 0f
            }
            renderer.setWalkPhase(walkDistancePx)
            renderer.setMirrorX(!frame.facingRight)
            renderer.render(frameTimeNanos)
            overlayView?.invalidate()

            if (looping) choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addOverlay()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, f)
        }
        // startLoop() 由 addOverlay() 内的协程在 loadPet 完成后调用
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        stopLoop()
        serviceScope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        renderer.dispose()
        bubbleLayer.dismiss()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addOverlay() {
        val dm = resources.displayMetrics
        val sizePx = (140 * dm.density).toInt()

        // 初始化运动引擎尺寸
        motionEngine.screenW = dm.widthPixels
        motionEngine.screenH = dm.heightPixels
        motionEngine.petSize = sizePx
        motionEngine.reset()   // 贴地，屏幕中央

        layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = motionEngine.x.toInt()
            y = motionEngine.y.toInt()
        }

        val view = PetOverlayView(
            context = this,
            renderer = renderer,
            behaviorEngine = behaviorEngine,
            onDragStart = {
                motionEngine.onDragStart()
            },
            onDrag = { rawX, rawY ->
                // 拖拽：直接同步位置到运动引擎
                val newX = rawX - sizePx / 2f
                val newY = rawY - sizePx / 2f
                motionEngine.setPosition(newX, newY)
                layoutParams.x = newX.toInt().coerceIn(0, dm.widthPixels - sizePx)
                layoutParams.y = newY.toInt().coerceIn(0, dm.heightPixels - sizePx)
                windowManager.updateViewLayout(overlayView, layoutParams)
            },
            onDragEnd = {
                motionEngine.onDragEnd()
            },
            onTap = {
                motionEngine.onTap()
            },
        )
        overlayView = view
        renderer.setViewport(sizePx, sizePx)
        windowManager.addView(view, layoutParams)

        // 先加载素材，完成后再启动渲染循环（避免渲染先于帧加载，显示占位猫）
        serviceScope.launch {
            val pet = petRepository.currentPet.first()
            val path = pet?.let { petRepository.assetPath(it.id) } ?: ""
            withContext(Dispatchers.IO) {
                renderer.loadPet(assetPath = path, type = PetAssetType.SPRITE_2D)
            }
            petLoaded = true
            startLoop()
        }
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        looping = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        startForeground(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL_ID = "overlay_pet"
        private const val NOTIFICATION_ID = 1001

        @Volatile var isRunning: Boolean = false
            private set

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OverlayService::class.java))
    }
}
