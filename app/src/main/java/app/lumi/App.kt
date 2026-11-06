package app.lumi

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.lumi.ui.ChatScreen
import app.lumi.ui.LumiTheme
import app.lumi.ui.SettingsScreen

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        Store.init(this)
        Local.refresh()
        Tools.ctx = this
    }

    companion object { lateinit var ctx: Context }
}

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val prefs by vm.prefs.collectAsState()
            LumiTheme(prefs.theme, prefs.dynamic) {
                var settings by rememberSaveable { mutableStateOf(false) }
                val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() } }
                BackHandler(settings) { settings = false }
                AnimatedContent(
                    targetState = settings,
                    transitionSpec = {
                        (slideInHorizontally { w -> if (targetState) w else -w / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { w -> if (targetState) -w / 3 else w } + fadeOut())
                    },
                    label = "screen",
                ) { s ->
                    if (s) SettingsScreen(vm) { settings = false } else ChatScreen(vm) { settings = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) { if (i?.action == Intent.ACTION_SEND) vm.shared(i) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("remind", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            (now() % Int.MAX_VALUE).toInt(),
            Notification.Builder(c, "remind").setSmallIcon(R.drawable.ic_stat).setContentTitle("Reminder")
                .setContentText(i.getStringExtra("text")).setStyle(Notification.BigTextStyle().bigText(i.getStringExtra("text")))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }
}
