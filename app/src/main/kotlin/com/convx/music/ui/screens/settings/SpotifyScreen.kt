package com.convx.music.ui.screens.settings

import com.convx.music.ui.ios.compat.AppTheme

import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.webkit.CookieManager
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.webkit.WebResourceRequest
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.webkit.WebView
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.webkit.WebViewClient
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.LinearEasing
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.RepeatMode
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.animateFloat
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.infiniteRepeatable
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.rememberInfiniteTransition
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.core.tween
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.background
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.graphics.graphicsLayer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.*
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.items
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.rememberScrollState
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.shape.CircleShape
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.verticalScroll
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.*
import com.convx.music.ui.ios.compat.ModalBottomSheet
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.*
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Alignment
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.draw.clip
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.graphics.Color
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.layout.ContentScale
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.platform.LocalContext
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.painterResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.stringResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.text.font.FontWeight
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.unit.dp
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.viewinterop.AndroidView
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.hilt.navigation.compose.hiltViewModel
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.navigation.NavController
import com.convx.music.ui.utils.appTopBarWindowInsets
import coil3.compose.AsyncImage
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.music.spotify.SpotifyAuth
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.R
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.DefaultDialog
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.viewmodels.SpotifyImportViewModel
import com.convx.music.ui.utils.appTopBarWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showPlaylistsSheet by remember { mutableStateOf(false) }
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()

    val refreshEnabled = state.isAuthenticated && !state.isLoading
    val rotationAngle by if (state.isLoading) {
        val transition = rememberInfiniteTransition(label = "rotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotationAngle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.spotify),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 24.dp,
    ) {
        // Connection Card/Group
        IosSettingsGroup(
            title = stringResource(R.string.spotify_account),
            items = listOf(
                if (state.isAuthenticated) {
                    IosSettingsItem(
                        leadingContent = if (!state.accountAvatarUrl.isNullOrBlank()) {
                            {
                                AsyncImage(
                                    model = state.accountAvatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else null,
                        icon = if (state.accountAvatarUrl.isNullOrBlank()) painterResource(R.drawable.spotify) else null,
                        title = {
                            Text(
                                text = if (state.accountName.isNotBlank()) state.accountName
                                else stringResource(R.string.spotify_account),
                                color = AppTheme.colorScheme.primary,
                                style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = AppTheme.colorScheme.surfaceContainer,
                                    contentColor = AppTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.action_logout))
                            }
                        },
                        onClick = {}
                    )
                } else {
                    IosSettingsItem(
                        title = { Text(stringResource(R.string.spotify_connect)) },
                        description = { Text(stringResource(R.string.spotify_not_connected)) },
                        icon = painterResource(R.drawable.spotify),
                        onClick = { showSpotifyLogin = true }
                    )
                }
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        val totalPlaylists = state.playlists.size + if (state.likedSongsCount > 0) 1 else 0
        IosSettingsGroup(
            title = stringResource(R.string.playlists),
            items = listOf(
                IosSettingsItem(
                    title = { Text(stringResource(R.string.spotify_select_sources)) },
                    description = {
                        Text(
                            if (state.isAuthenticated) {
                                if (totalPlaylists > 0) stringResource(R.string.spotify_available_count, totalPlaylists)
                                else stringResource(R.string.spotify_no_sources)
                            } else {
                                stringResource(R.string.spotify_not_connected)
                            }
                        )
                    },
                    icon = painterResource(R.drawable.bookmark_star_library),
                    enabled = state.isAuthenticated && totalPlaylists > 0 && !state.isLoading,
                    onClick = { showPlaylistsSheet = true }
                ),
                IosSettingsItem(
                    title = { Text(stringResource(R.string.spotify_refresh)) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    AppTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                tint = if (!refreshEnabled) {
                                    AppTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    AppTheme.colorScheme.primary.copy(alpha = 0.9f)
                                },
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = rotationAngle
                                    }
                            )
                        }
                    },
                    enabled = refreshEnabled,
                    onClick = { viewModel.loadSources() }
                )
            )
        )

        // Info block
        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                tint = AppTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = stringResource(R.string.spotify_import_desc),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                showSpotifyLogin = false
                viewModel.connectWithCookies(spDc, spKey)
            }
        )
    }

    importProgress?.let { progress ->
        val isFinished = progress.isFinished || progress.percent >= 1f
        DefaultDialog(
            onDismiss = {
                if (isFinished) {
                    viewModel.dismissImportProgress()
                } else {
                    viewModel.cancelImport()
                }
            },
            title = {
                Text(
                    text = if (isFinished) {
                        stringResource(R.string.spotify_import_complete)
                    } else {
                        stringResource(R.string.spotify_import_in_progress)
                    }
                )
            },
            buttons = {
                if (isFinished) {
                    Button(
                        onClick = { viewModel.dismissImportProgress() },
                        shape = CircleShape
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                } else {
                    TextButton(onClick = { viewModel.cancelImport() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.playlists) + ": " + progress.playlistName,
                    style = AppTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isFinished) {
                        stringResource(R.string.spotify_imported_songs_count, progress.currentSongIndex, progress.totalSongs)
                    } else {
                        stringResource(R.string.spotify_importing_songs_count, progress.currentSongIndex, progress.totalSongs)
                    },
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = AppTheme.colorScheme.primary,
                    trackColor = AppTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
    state.errorMessage?.let { error ->
        DefaultDialog(
            onDismiss = { viewModel.dismissError() },
            title = { Text("Error") },
            buttons = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            Text(
                text = error,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPlaylistsSheet) {
        SpotifyPlaylistBottomSheet(
            onDismiss = { showPlaylistsSheet = false },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyLoginSheet(
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var captured by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
            webView = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = AppTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.spotify_login_title),
                style = AppTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.spotify_waiting_for_login),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(AppTheme.shapes.large),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            private fun captureCookies(url: String?): Boolean {
                                if (captured) return true
                                cookieManager.flush()
                                val domainsToCheck = listOfNotNull(
                                    url,
                                    "https://open.spotify.com",
                                    "https://accounts.spotify.com",
                                    "https://spotify.com",
                                    ".spotify.com"
                                )
                                for (domain in domainsToCheck) {
                                    val cookiesStr = cookieManager.getCookie(domain) ?: continue
                                    val cookies = cookiesStr.split(";").associate {
                                        val parts = it.split("=")
                                        val key = parts.firstOrNull()?.trim().orEmpty()
                                        val valStr = parts.drop(1).joinToString("=").trim()
                                        key to valStr
                                    }
                                    val spDc = cookies["sp_dc"].orEmpty()
                                    if (spDc.isNotBlank()) {
                                        captured = true
                                        onCookiesCaptured(spDc, cookies["sp_key"].orEmpty())
                                        return true
                                    }
                                }
                                return false
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = captureCookies(request.url?.toString())

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                captureCookies(url)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                captureCookies(url)
                            }
                        }
                        webChromeClient = android.webkit.WebChromeClient()
                        webView = this
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                },
                update = { view ->
                    webView = view
                },
            )
        }
    }
}
