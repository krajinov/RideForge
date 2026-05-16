package com.delminiusapps.rideforge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.delminiusapps.rideforge.data.local.configureRideForgeLocalStorageContext
import com.delminiusapps.rideforge.data.trainer.configureTrainerBluetoothContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        configureRideForgeLocalStorageContext(applicationContext)
        configureTrainerBluetoothContext(applicationContext)
        requestBlePermissions()

        setContent {
            App()
        }
    }

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), BlePermissionRequestCode)
        }
    }

    private companion object {
        const val BlePermissionRequestCode = 2601
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
