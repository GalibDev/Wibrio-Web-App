package com.wibrio.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wibrio.mesh.MeshStatus
import com.wibrio.mesh.TransportPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/** User-started, bounded session. Never restarts radios after boot, force-stop or process death. */
@AndroidEntryPoint class MeshForegroundService : Service() {
    @Inject lateinit var mesh: DirectMeshRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var starting = 0
    private var latestStartId = 0
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == STOP) { mesh.shutdown(); stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Active mesh", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, MeshForegroundService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mesh_notification)
                .setContentTitle("Wibrio mesh active").setContentText("Verified nearby links · session up to 30 minutes")
                .setContentIntent(open).setOngoing(true).addAction(0, "Stop mesh", stop).build()
            ServiceCompat.startForeground(this, 4, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            starting++
            scope.launch {
                try {
                    mesh.configureName(intent?.getStringExtra("name").orEmpty())
                    mesh.start(TransportPreference.AUTOMATIC)
                } finally { starting-- }
                if ((mesh.status.value is MeshStatus.Inactive || mesh.status.value is MeshStatus.Unavailable) && starting == 0) stopSelf(latestStartId)
                else if (observer == null) observer = launch {
                    mesh.status.collect { if ((it is MeshStatus.Inactive || it is MeshStatus.Unavailable) && starting == 0) stopSelf(latestStartId) }
                }
            }
        } catch (failure: Exception) { mesh.error.value = "Mesh foreground service could not start: ${failure.message?.take(120)}"; mesh.shutdown(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); scope.cancel(); mesh.shutdown(); super.onDestroy() }
    companion object { private const val CHANNEL = "wibrio_mesh"; private const val STOP = "com.wibrio.app.STOP_MESH" }
}
