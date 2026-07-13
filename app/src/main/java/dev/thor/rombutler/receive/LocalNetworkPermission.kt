package dev.thor.rombutler.receive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Android 17 runtime permission required for direct incoming LAN connections. */
object LocalNetworkPermission {

    const val ANDROID_17_API_LEVEL = 37

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < ANDROID_17_API_LEVEL ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED
}
