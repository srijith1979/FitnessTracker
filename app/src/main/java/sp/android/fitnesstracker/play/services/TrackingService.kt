package sp.android.fitnesstracker.play.services


import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sp.android.fitnesstracker.play.R
import sp.android.fitnesstracker.play.ui.MainActivity
import sp.android.fitnesstracker.play.util.Constants.ACTION_PAUSE_SERVICE
import sp.android.fitnesstracker.play.util.Constants.ACTION_SHOW_TRACKING_FRAGMENT
import sp.android.fitnesstracker.play.util.Constants.ACTION_START_OR_RESUME_SERVICE
import sp.android.fitnesstracker.play.util.Constants.ACTION_STOP_SERVICE
import sp.android.fitnesstracker.play.util.Constants.FASTEST_LOCATION_INTERVAL
import sp.android.fitnesstracker.play.util.Constants.LOCATION_UPDATE_INTERVAL
import sp.android.fitnesstracker.play.util.Constants.NOTIFICATION_CHANNEL_ID
import sp.android.fitnesstracker.play.util.Constants.NOTIFICATION_CHANNEL_NAME
import sp.android.fitnesstracker.play.util.Constants.NOTIFICATION_ID
import sp.android.fitnesstracker.play.util.Constants.TIMER_UPDATE_INTERVAL
import sp.android.fitnesstracker.play.util.TrackingUtility
import timber.log.Timber
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    var isFirstRun = true
    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder
    lateinit var curNotificationBuilder: NotificationCompat.Builder


    private val timeRunInSeconds = MutableLiveData<Long>()
    //timer variables
    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    companion object {
        val isTracking = MutableLiveData<Boolean>()
        val multiPathPoints = MutableLiveData<Polylines>()
        val timeRunInMilliSeconds = MutableLiveData<Long>()
    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        multiPathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeRunInMilliSeconds.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        postInitialValues()

        isTracking.observe(this, Observer {
            updateLocationTracking(it)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                    } else {
                        Timber.d("Resuming service...")
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    Timber.d("Paused service")
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    Timber.d("Stopped service")
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationTracking(isTracking: Boolean) {
        if (isTracking) {
            if (TrackingUtility.hasLocationPermissions(this)) {
                val request = LocationRequest().apply {
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = FASTEST_LOCATION_INTERVAL
                    priority = PRIORITY_HIGH_ACCURACY
                }
                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            if (isTracking.value!!) {
                result?.locations?.let { locations ->
                    for (location in locations) {
                        addPathPoint(location)
                        Timber.d("NEW LOCATION: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            multiPathPoints.value?.apply {
                last().add(pos)
                multiPathPoints.postValue(this)
            }
        }
    }

    private fun addEmptyPolyline() = multiPathPoints.value?.apply {
        add(mutableListOf())
        multiPathPoints.postValue(this)
    } ?: multiPathPoints.postValue(mutableListOf(mutableListOf()))

    private fun startForegroundService() {
        startTimer()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }


    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                // time difference between now and timeStarted
                lapTime = System.currentTimeMillis() - timeStarted
                // post the new lapTime
                timeRunInMilliSeconds.postValue(timeRun + lapTime)
                if (timeRunInMilliSeconds.value!! >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 1)
                    lastSecondTimestamp += 1000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }
}
