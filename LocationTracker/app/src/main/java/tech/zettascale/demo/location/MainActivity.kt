package tech.zettascale.demo.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import tech.zettascale.demo.location.ui.theme.LocationTrackerTheme
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import io.zenoh.Session
import io.zenoh.Config
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.*



class MainActivity : ComponentActivity() {
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    lateinit var lm: LocationManager
    lateinit var provider: String
    var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            LocationTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Tracker(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding),
                        this
                    )
                }
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun isLocationPermissionGranted(): Boolean {
        val perm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return  perm == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun locationToCarData(l: Location, model: String, color: String): String =
        """
            "position": {
                "lat": ${l.latitude},
                "lng": ${l.longitude}
            },
            "speed": ${l.speed},
            "color": "${color}"
            "id": "${model}"            
        """.trimIndent()

    @SuppressLint("MissingPermission")
    public fun startLocationTracking(
        locator: String,
        key: String,
        model: String,
        color: String) {
        Log.d("ZLocTracker", "[][][][][][]Starting Location Tracking for:\n $locator, $model, $color, $key")
        val config_json = """
         {
             "mode": "client",
             "connect": {
                 "endpoints": [
                     "${locator}"
                 ]
             },
             "scouting": {
                 "multicast": {
                     "enabled": false
                 }
             }
         }
        """.trimIndent()

        val kexpr = key.intoKeyExpr().getOrThrow()
//        val c = Config.from(config_json)
        val c = Config.default()
        val z = Session.open(c).getOrThrow()
        val zpub = z.declarePublisher(kexpr)
            .congestionControl(CongestionControl.DROP)
            .priority(Priority.REALTIME)
            .res().getOrThrow()

        if (!isLocationPermissionGranted()) {
            requestLocationPermission()
        }

        Log.d("ZLoc", ">>>>>>>> Creating Activity")
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        provider = lm.allProviders.find { s -> s == "gps" } ?: "gps"

        locationListener =
            LocationListener { l ->
                zpub.put(locationToCarData(l, model, color)).res()
                Log.d("ZLocTracker", ">>>>>>>> Published Location: $l")
            }

        val p: String = provider
        val t: Long = 1000
        val d: Float = 0f

        lm.requestLocationUpdates(p, t, d, locationListener!!, Looper.getMainLooper())
    }
    fun stopLocationTracking() {
        if (locationListener != null) {
            lm.removeUpdates(locationListener!!)
        }
    }

}

@Composable
fun Tracker(name: String, modifier: Modifier = Modifier, activity: MainActivity) {
    var locator by remember { mutableStateOf("tcp//172.31.3.52:7447") }
    var model by remember { mutableStateOf("Ducati Scrambler 1100") }
    var color by remember { mutableStateOf("Black") }
    var key by remember { mutableStateOf("demo/vehicles/location") }
    var running by remember {mutableStateOf(false)}
    Box(modifier = modifier,
        contentAlignment = Alignment.Center) {

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PaaS:")
                TextField(
                    value = locator,
                    onValueChange = { s -> locator = s}, enabled = !running)
            }
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Model:")
                TextField(value = model, onValueChange = { s -> model = s}, enabled = !running)
            }
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color:")
                TextField(value = color, onValueChange = { s -> color = s}, enabled = !running)
            }
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Key:")
                TextField(value = key, onValueChange = { s -> key = s}, enabled = !running)
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = !running,
                    colors = ButtonColors(Color.Green, Color.Black, Color.Gray, Color.Black),
                    onClick = {
                        running = true
                        activity.startLocationTracking(locator, key, model, color)
                    })
                {
                    Text("Start")
                }
                Button(enabled = running,
                    colors = ButtonColors(Color.Red, Color.Black, Color.Gray, Color.Black),
                    onClick = {
                        running = false
                        activity.stopLocationTracking()
                    }
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

