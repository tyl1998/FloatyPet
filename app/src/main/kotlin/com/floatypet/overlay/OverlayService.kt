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
import com.floatypet.overlay.behavior.PetBehaviorEngine
import com.floatypet.overlay.render.BubbleLayer
import com.floatypet.overlay.render.NoOpBubbleLayer
import com.floatypet.overlay.render.PetRenderer
import com.floatypet.overlay.render.SpriteRenderer

/**
 * 悬浮窗前台服务。
 *
 * 进程铁律（CLAUDE.md §7）：不自启、不关联唤醒；仅用户主动开启时启动；
 * 前台服务 + LOW 通知保活，通知可隐藏；**息屏停止渲染循环**。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: PetOverlayView? = null

    // 渲染隔离：只持有接口（CLAUDE.md §5.3）
    private val renderer: PetRenderer = SpriteRenderer()
    private val bubbleLayer: BubbleLayer = NoOpBubbleLayer()
    private val behaviorEngine = PetBehaviorEngine()

    private val choreographer = Choreographer.getInstance()
    private var looping = false

    // 息屏停渲染、亮屏恢复
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopLoop()
                Intent.ACTION_SCREEN_ON -> startLoop()
            }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
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
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        stopLoop()
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
        val sizePx = (140 * resources.displayMetrics.density).toInt()
        layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢焦点、不拦键盘；可超出屏幕计算便于吸附
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx
            y = resources.displayMetrics.heightPixels / 2
        }

        renderer.loadPet(assetPath = "", type = PetAssetType.SPRITE_2D)

        val view = PetOverlayView(
            context = this,
            renderer = renderer,
            behaviorEngine = behaviorEngine,
            onDrag = { dx, dy ->
                layoutParams.x += dx
                layoutParams.y += dy
                windowManager.updateViewLayout(overlayView, layoutParams)
            },
            onDragEnd = { snapToEdge() },
        )
        overlayView = view
        windowManager.addView(view, layoutParams)
    }

    /** 松手吸附到最近的左右边缘（留 8dp 安全间距）。 */
    private fun snapToEdge() {
        val view = overlayView ?: return
        val screenW = resources.displayMetrics.widthPixels
        val margin = (8 * resources.displayMetrics.density).toInt()
        val centerX = layoutParams.x + view.width / 2
        layoutParams.x = if (centerX < screenW / 2) {
            margin
        } else {
            screenW - view.width - margin
        }
        windowManager.updateViewLayout(view, layoutParams)
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "overlay_pet"
        private const val NOTIFICATION_ID = 1001

        /** 当前悬浮窗是否在运行。供 UI 切换按钮态。 */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 由用户主动开启悬浮窗时调用，不在任何广播/启动钩子里自动拉起。 */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
