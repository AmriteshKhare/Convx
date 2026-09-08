/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.ExtendedFloatingActionButton
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Scaffold
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.convx.music.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowshapeTurnUpLeft
import com.composables.sfsymbols.monochrome.SFSquareOnSquare
import com.composables.sfsymbols.monochrome.SFXmark
import com.convx.music.ui.theme.vivimusicTheme
import com.convx.music.utils.CrashHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val crashLog = intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG) ?: getString(R.string.crash_no_log)
        
        setContent {
            val darkTheme = isSystemInDarkTheme()
            vivimusicTheme(darkTheme = darkTheme) {
                CrashScreen(
                    crashLog = crashLog,
                    onClose = { finishAffinity() },
                    onShare = { shareCrashLog(crashLog) },
                    onCopy = { copyToClipboard(crashLog) }
                )
            }
        }
    }

    private fun copyToClipboard(crashLog: String) {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("CrashLog", crashLog)
        cm.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, R.string.copied_to_clipboard, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun shareCrashLog(crashLog: String) {
        try {
            // Create crash log file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "convx_crash_$timestamp.txt"
            val crashFile = File(cacheDir, fileName)
            crashFile.writeText(crashLog)
            
            // Get URI using FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.FileProvider",
                crashFile
            )
            
            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        } catch (e: Exception) {
            // Fallback to simple text share if file sharing fails
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, crashLog)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_subject))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_share_title)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(
    crashLog: String,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.crash_title),
                        style = AppTheme.typography.headlineSmall
                    ) 
                },
                actions = {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = SfSymbols.Monochrome.SFSquareOnSquare,
                            contentDescription = stringResource(R.string.copy_logs)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = SfSymbols.Monochrome.SFXmark,
                            contentDescription = stringResource(R.string.crash_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colorScheme.surface
                ),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onShare,
                icon = {
                    Icon(
                        imageVector = SfSymbols.Monochrome.SFArrowshapeTurnUpLeft,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.crash_share_logs)) },
                containerColor = AppTheme.colorScheme.primaryContainer,
                contentColor = AppTheme.colorScheme.onPrimaryContainer
            )
        },
        containerColor = AppTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.crash_description),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Crash log container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colorScheme.surfaceContainerHighest)
                    .padding(16.dp)
            ) {
                Text(
                    text = crashLog,
                    style = AppTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = AppTheme.colorScheme.onSurface,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
            
            // Bottom spacing for FAB
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
