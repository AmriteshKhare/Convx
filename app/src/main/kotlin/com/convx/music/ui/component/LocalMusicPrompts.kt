/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.convx.music.R
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Text

/** The runtime permission that gates reading audio files on this Android version. */
fun localAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasLocalAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, localAudioPermission()) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * Asks for access to on-device audio and tells the caller how it went.
 *
 * Once Android has stopped showing the system dialog (two denials, or "don't ask again"),
 * launching the request again does nothing, so a button that keeps asking looks broken. This
 * reports that state so the prompt can send the user to app settings instead.
 */
@Composable
fun rememberLocalAudioPermission(onResult: (granted: Boolean) -> Unit): LocalAudioPermission {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? Activity
        blocked = !granted && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, localAudioPermission())
        onResult(granted)
    }
    return remember(launcher, blocked) {
        LocalAudioPermission(
            blocked = blocked,
            request = { launcher.launch(localAudioPermission()) },
        )
    }
}

class LocalAudioPermission(val blocked: Boolean, val request: () -> Unit)

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Full-width prompt shown where local music needs storage access, in place of the one-line
 * "Grant permission" text this used to be. Says what is being asked for and why, and switches to
 * "Open Settings" when the system dialog can no longer be shown.
 */
@Composable
fun LocalMusicPermissionPrompt(
    permission: LocalAudioPermission,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    PromptLayout(
        icon = R.drawable.library_music,
        title = stringResource(R.string.local_permission_title),
        body = stringResource(
            if (permission.blocked) R.string.local_permission_blocked_body else R.string.local_permission_body,
        ),
        modifier = modifier,
    ) {
        Button(onClick = { if (permission.blocked) openAppSettings(context) else permission.request() }) {
            Text(
                stringResource(
                    if (permission.blocked) R.string.local_permission_open_settings else R.string.local_permission_allow,
                ),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Shown when access is granted but the device scan found no audio. */
@Composable
fun LocalMusicEmptyState(
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PromptLayout(
        icon = R.drawable.music_note,
        title = stringResource(R.string.local_empty_title),
        body = stringResource(R.string.local_empty_body),
        modifier = modifier,
    ) {
        Button(onClick = onScan) {
            Text(stringResource(R.string.local_empty_scan), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PromptLayout(
    icon: Int,
    title: String,
    body: String,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(AppTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AppTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        action()
    }
}
