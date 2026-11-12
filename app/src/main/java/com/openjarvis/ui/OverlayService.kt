package com.openjarvis.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.graphify.nodes.TaskNode
import com.openjarvis.ui.overlay.FloatingOverlayWidget
import com.openjarvis.ui.theme.OpenJarvisTheme
import com.openjarvis.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OverlayService : LifecycleService() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private lateinit var agentCore: AgentCore
    private lateinit var voiceManager: VoiceManager
    private lateinit var graphifyRepo: GraphifyRepository
    private val recentTasks = MutableStateFlow<List<TaskNode>>(emptyList())

    override fun onCreate() {
        super.onCreate()

        agentCore = AgentCore(this)
        voiceManager = VoiceManager(this)
        graphifyRepo = GraphifyRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, createNotification())
        showOverlay()

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { graphifyRepo.getRecentTasks(10) }
                .onSuccess { tasks -> recentTasks.value = tasks }
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            ViewTreeLifecycleOwner.set(this, this@OverlayService)
            setContent {
                val agentState = agentCore.state.collectAsState()
                val tasks = recentTasks.collectAsState()
                OpenJarvisTheme {
                    Box(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        FloatingOverlayWidget(
                            agentState = agentState.value,
                            voiceManager = voiceManager,
                            recentTasks = tasks.value,
                            onCommand = agentCore::executeTask,
                            onCollapse = {}
                        )
                    }
                }
            }
        }

        val width = (320 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * resources.displayMetrics.density).toInt()
            y = (150 * resources.displayMetrics.density).toInt()
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
        } catch (error: Exception) {
            view.disposeComposition()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
            view.disposeComposition()
        }
        overlayView = null
        voiceManager.release()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun executeCommand(command: String) {
        agentCore.executeTask(command)
        lifecycleScope.launch(Dispatchers.IO) {
            val tasks = graphifyRepo.getRecentTasks(10)
            recentTasks.value = tasks
        }
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_overlay_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }
    }
}
