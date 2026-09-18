/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Scaffold
import com.convx.music.ui.ios.compat.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.crossfade
import com.convx.music.R
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.ios.foundation.LocalIosBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberNavTransitionFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberPagerTransitionFreeze
import com.convx.music.ui.utils.Motion
import com.convx.music.ui.utils.LocalSharedTransitionScope
import com.convx.music.ui.utils.markSharedArtworkSource
import com.convx.music.ui.utils.markMorphDirection
import com.convx.music.ui.utils.morphArtworkId
import com.convx.music.ui.utils.isMorphRoute
import com.convx.music.ui.component.prewarmHeroTint
import com.convx.music.ui.player.prewarmMeshPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.convx.music.constants.AppBarHeight
import com.convx.music.constants.AppLanguageKey
import com.convx.music.constants.DarkModeKey
import com.convx.music.constants.DefaultOpenTabKey
import com.convx.music.constants.DisableScreenshotKey
import com.convx.music.constants.SearchSource
import com.convx.music.constants.LocalOnlyModeKey
import com.convx.music.constants.SearchSourceKey
import com.convx.music.constants.EnableHighRefreshRateKey
import com.convx.music.constants.EnableSettingsPopupKey
import com.convx.music.constants.ListenTogetherInTopBarKey
import com.convx.music.constants.ListenTogetherUsernameKey
import com.convx.music.constants.MiniPlayerBottomSpacing
import com.convx.music.constants.MiniPlayerHeight
import com.convx.music.constants.DockedAccessoryHeight
import com.convx.music.constants.NavigationBarAnimationSpec
import com.convx.music.constants.NavigationBarHeight
import com.convx.music.constants.PauseListenHistoryKey
import com.convx.music.constants.LiquidGlassGlobalEnabledKey
import com.convx.music.constants.LiquidGlassMiniPlayerEnabledKey
import com.convx.music.constants.LiquidGlassNavBarEnabledKey
import com.convx.music.constants.LiquidGlassSidePanelEnabledKey
import com.convx.music.constants.LiquidGlassSidePanelVibrancyKey
import com.convx.music.constants.LiquidGlassSidePanelBlurRadiusKey
import com.convx.music.constants.LiquidGlassSidePanelLensHeightKey
import com.convx.music.constants.LiquidGlassSidePanelLensAmountKey
import com.convx.music.constants.LiquidGlassSidePanelColorKey
import com.convx.music.constants.LiquidGlassSidePanelSurfaceOpacityKey
import com.convx.music.constants.LiquidGlassSidePanelTextColorKey
import com.convx.music.constants.SideBarCollapsedKey
import com.convx.music.constants.LiquidGlassVibrancyKey
import com.convx.music.constants.LiquidGlassBlurRadiusKey
import com.convx.music.constants.LiquidGlassLensHeightKey
import com.convx.music.constants.LiquidGlassLensAmountKey
import com.convx.music.constants.LiquidGlassChromaticAberrationKey
import com.convx.music.constants.LiquidGlassDepthEffectKey
import com.convx.music.constants.LiquidGlassSurfaceTintColorKey
import com.convx.music.constants.LiquidGlassPuckColorKey
import com.convx.music.constants.LiquidGlassPuckOpacityKey
import com.convx.music.constants.LiquidGlassStyleKey
import com.convx.music.constants.LiquidGlassHighlightColorKey
import com.convx.music.constants.LiquidGlassHighlightOpacityKey
import com.convx.music.constants.LiquidGlassSurfaceOpacityKey
import com.convx.music.constants.LiquidGlassAdaptiveContrastKey
import com.convx.music.constants.LiquidGlassTextColorKey
import com.convx.music.constants.ShowHistoryButtonKey
import com.convx.music.constants.ShowStatsButtonKey
import com.convx.music.constants.AppleMusicUiKey
import com.convx.music.constants.PauseSearchHistoryKey
import com.convx.music.constants.AppBackgroundColorKey
import com.convx.music.constants.PureBlackKey
import com.convx.music.constants.SYSTEM_DEFAULT
import com.convx.music.constants.SelectedThemeColorKey
import com.convx.music.constants.SlimNavBarHeight
import com.convx.music.constants.ForceTabletLayoutKey
import com.convx.music.constants.PlaylistSortType
import com.convx.music.constants.SlimNavBarKey
import com.convx.music.constants.StopMusicOnTaskClearKey
import com.convx.music.constants.UseNewMiniPlayerDesignKey
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.SearchHistory
import com.convx.music.extensions.toEnum
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.DownloadUtil
import com.convx.music.playback.MusicService
import com.convx.music.playback.MusicService.MusicBinder
import com.convx.music.playback.PlayerConnection
import com.convx.music.playback.queues.YouTubeQueue
import com.convx.music.ui.component.AppFloatingNavBar
import com.convx.music.ui.component.LocalDownloads
import com.convx.music.ui.component.LocalItemPrefs
import com.convx.music.ui.component.LocalNavSearchState
import com.convx.music.ui.component.rememberAppBackgroundTint
import com.convx.music.ui.component.rememberItemPrefs
import com.convx.music.ui.component.FrostedTopBar
import com.convx.music.ui.component.LocalHomeTopBarLogoProgress
import com.convx.music.ui.component.TopFadeBlur
import com.convx.music.ui.component.NavSearchState
import com.convx.music.ui.component.GlassEffectConfig
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.LocalAppBackdrop
import com.convx.music.ui.component.LocalAppBackdropFrozen
import com.convx.music.ui.component.GlassStyle
import com.convx.music.ui.component.glassContentColorFor
import com.convx.music.ui.component.backdrop.GlassPoolScale
import com.convx.music.ui.component.backdrop.rememberGlassPool
import com.convx.music.ui.component.backdrop.glassPool
import com.convx.music.ui.component.backdrop.BackdropEffectScope
import com.convx.music.ui.component.backdrop.effects.blur
import com.convx.music.ui.component.backdrop.effects.colorControls
import com.convx.music.ui.component.PooledGlass
import com.convx.music.ui.component.LocalPooledGlass
import com.convx.music.ui.component.glassSaturation
import com.convx.music.ui.component.isGlassAllowed
import com.convx.music.ui.component.AppFloatingNowPlayingPill
import com.convx.music.ui.component.FloatingMiniPlayerWidthFraction
import com.convx.music.ui.component.NavBarSearchInputBar
import com.convx.music.ui.component.GlassCircleButton
import com.convx.music.ui.component.AppFloatingSideBar
import com.convx.music.ui.component.SideBarAccountRow
import com.convx.music.ui.component.SideBarContentInset
import com.convx.music.ui.component.SideBarMargin
import com.convx.music.ui.component.SideBarCollapsedWidth
import com.convx.music.ui.component.SideBarLink
import com.convx.music.ui.component.SideBarSection
import com.convx.music.ui.component.TabletWidthThreshold
import com.convx.music.ui.component.OverlayMenu
import com.convx.music.ui.component.BottomSheetPage
import com.convx.music.ui.component.ListenTogetherOverlay
import com.convx.music.ui.component.QueueConflictDialog
import com.convx.music.ui.component.QueueReplacedUndoSnackbar
import com.convx.music.ui.component.LocalBottomSheetPageState
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.iosSheetPresentation
import com.convx.music.ui.component.LocalSheetPresentation
import com.convx.music.ui.component.SheetPresentationState
import com.convx.music.ui.component.rememberSheetPresentation
import com.convx.music.ui.component.rememberBottomSheetState
import com.convx.music.ui.component.shimmer.ShimmerTheme
import com.convx.music.ui.menu.YouTubeSongMenu
import com.convx.music.ui.player.BottomSheetPlayer
import com.convx.music.ui.player.InstallPlayerMorphLayers
import com.convx.music.ui.player.PlayerMorph
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import com.convx.music.ui.screens.Screens
import com.convx.music.ui.screens.SettingDialoge
import com.convx.music.ui.screens.navigationBuilder
import com.convx.music.ui.screens.settings.DarkMode
import com.convx.music.ui.screens.settings.NavigationTab
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.BrandName
import androidx.compose.ui.graphics.isSpecified
import com.convx.music.ui.theme.DefaultThemeColor
import com.convx.music.ui.theme.GlassTintTokens
import com.convx.music.ui.theme.LocalAccentTextColor
import com.convx.music.ui.theme.isHueless
import com.convx.music.ui.theme.rememberBrandFontFamily
import com.convx.music.ui.theme.vivimusicTheme
import com.convx.music.ui.utils.appBarScrollBehavior
import com.convx.music.ui.utils.resetHeightOffset
import com.convx.music.utils.SyncUtils
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.utils.rememberPreference
import com.convx.music.constants.IosOverscrollKey
import com.convx.music.ui.utils.rememberIosOverscrollFactory
import com.convx.music.utils.reportException
import com.convx.music.utils.setAppLocale
import com.convx.music.viewmodels.HistoryViewModel
import com.convx.music.ui.component.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.convx.music.viewmodels.HomeViewModel
import com.convx.music.vivimusic.UpdateNotificationHelper
import com.convx.music.vivimusic.updater.checkForUpdate
import com.convx.music.vivimusic.updater.getAutoUpdateCheckSetting
import com.convx.music.vivimusic.updater.getUpdateNotificationsSetting
import com.convx.music.vivimusic.updater.saveUpdateAvailableState
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


// DIAGNOSTIC ONLY â€” keep false. Bypasses the full-screen layerBackdrop pass so the cost of
// the glass pipeline can be measured against the cost of the content itself. Glass surfaces
// fall back to their translucent look while this is true, so it must never ship enabled.
// Measured 2026-08-05 on SM-M346B, Home scroll p50: true => record 1.9ms / issue 4.6ms,
// false => record 16.9ms / issue 19.7ms. The gap is the forced full-tree re-record in
// LayerBackdropNode.draw(); the fix is to give the subtree RenderNodes, not to drop glass.
private val TabRootRoutes = setOf(
    Screens.Home.route,
    Screens.Library.route,
    Screens.Explore.route,
    Screens.Settings.route,
    Screens.Songs.route,
)

private const val DIAG_DISABLE_BACKDROP = false
// Melox's measured-fast path lets the few visible chrome surfaces sample the
// shared backdrop directly. A full-screen pre-blur/flatten pool added ~4.5 ms
// issue-draw time on the SM-M346B when only nav/search/mini chrome consumed it.
private const val USE_SHARED_GLASS_POOL = false

/**
 * Routes whose whole screen is an `AndroidView` WebView. They opt out of the app
 * backdrop capture â€” see the NavHost modifier for why.
 */
private val WebViewRoutes = setOf(
    "login",
    "switch_channel",
    "settings/spotify",
    "settings/discord/login",
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTION_SEARCH = "com.convx.music.action.SEARCH"
        private const val ACTION_LIBRARY = "com.convx.music.action.LIBRARY"

        // Ignore a repeat bottom-nav navigate() to the same route within this
        // window, so the floating tab bar's predictive (press/drag) fire can't
        // double-navigate against its own release-fire.
        private const val NavDebounceMs = 250L

        // How long the nav bar's shrink/expand-to-pill animation takes before the
        // actual navigate()/navigateUp() call for entering/exiting search â€” keeps
        // the animation and the route's own screen transition from overlapping.
        // Comfortably past the crossfade's own ~300ms so it always finishes first.
        private const val SearchNavTransitionDelayMs = 360L
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var listenTogetherManager: com.convx.music.listentogether.ListenTogetherManager

    private var pendingIntent: Intent? = null

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                try {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    Timber.tag("MainActivity").d("PlayerConnection created successfully")
                    // Connect Listen Together manager to player
                    listenTogetherManager.setPlayerConnection(playerConnection)
                } catch (e: Exception) {
                    Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")
                    // Retry after a delay of 500ms
                    lifecycleScope.launch {
                        delay(500.milliseconds)
                        try {
                            playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                            listenTogetherManager.setPlayerConnection(playerConnection)
                        } catch (e2: Exception) {
                            Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Disconnect Listen Together manager
            listenTogetherManager.setPlayerConnection(null)
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onStart() {
        super.onStart()
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        // On Android 12+, we can't start foreground services from background
        // Use BIND_AUTO_CREATE which will create the service if needed
        // The service will call startForeground() in onCreate() when bound
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Covers a deliberate back-out, where the activity really is finishing. The swipe
        // -from-recents case is handled in MusicService.onTaskRemoved instead: there
        // isFinishing is false and this branch never ran, which is why the setting looked
        // broken to everyone who used the gesture rather than the back button.
        if (dataStore.get(StopMusicOnTaskClearKey, false) &&
            playerConnection?.isPlaying?.value == true &&
            isFinishing
        ) {
            // onStop() (always called before onDestroy()) already unbound
            // serviceConnection â€” unbinding again throws IllegalArgumentException
            // ("Service not registered"), tearing the audio session down via a
            // crash instead of a clean stop.
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Live handling goes through the addOnNewIntentListener registered once
        // composition mounts. This is a safety-net capture for the (unlikely)
        // window before that listener is registered; the listener will already
        // have handled the common case, so this is otherwise inert.
        pendingIntent = intent
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            com.convx.music.ui.component.MenuAnchor.lastDown =
                androidx.compose.ui.geometry.Offset(ev.x, ev.y)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Listen Together temporarily disabled — same "needs more work before shipping"
        // treatment PlayerSettings.kt already gives DJ mixing/creative transitions. Skipping
        // initialize() keeps the client from ever connecting or reconnecting to a persisted
        // room in the background; the CompositionLocal below is also nulled so every UI entry
        // point falls back to its existing "manager not available" state instead.
        // listenTogetherManager.initialize()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale = dataStore[AppLanguageKey]
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            vivimusicApp(
                playerConnection = playerConnection,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
            )
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalLayoutApi::class,
        ExperimentalSharedTransitionApi::class,
    )
    @Composable
    private fun vivimusicApp(
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            if (getAutoUpdateCheckSetting(context)) {
                // Delay to not block app startup
                delay(2000L.milliseconds)
                checkForUpdate(
                    context = context,
                    onSuccess = { latestVersion, isAvailable, _, _, _, _, _, _ ->
                        val currentVersion = BuildConfig.VERSION_NAME
                        Timber.tag("UpdateCheck").d("Startup check success. Latest: $latestVersion, Current: $currentVersion, isAvailable: $isAvailable")
                        saveUpdateAvailableState(context, isAvailable)
                        
                        if (isAvailable && getUpdateNotificationsSetting(context)) {
                            Timber.tag("UpdateCheck").d("Posting update notification for $latestVersion")
                            UpdateNotificationHelper.showUpdateNotification(context, latestVersion)
                        }
                    },
                    onError = {
                        Timber.tag("UpdateCheck").e("Startup check failed")
                        // Do not clear the state on error, in case of offline launch
                    }
                )
            }
        }

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                val modes = window.windowManager.defaultDisplay.supportedModes
                val currentMode = window.windowManager.defaultDisplay.mode
                if (enableHighRefreshRate) {
                    // preferredDisplayModeId = 0 means "no preference", i.e. exactly what
                    // the window already had. It does not ask for anything, so with the
                    // setting ON the app was leaving the choice to the platform, and an
                    // adaptive-refresh panel showing mostly-static content will happily
                    // settle at 60. Ask for the fastest mode explicitly instead.
                    //
                    // Restricted to modes at the current resolution: supportedModes also
                    // lists lower-resolution modes, and some devices offer their highest
                    // refresh rates only there. Switching resolution to chase a refresh
                    // rate would reflow the whole UI.
                    val fastest = modes
                        .filter {
                            it.physicalWidth == currentMode.physicalWidth &&
                                it.physicalHeight == currentMode.physicalHeight
                        }
                        .maxByOrNull { it.refreshRate }
                    layoutParams.preferredDisplayModeId = fastest?.modeId ?: 0
                } else {
                    val mode60 = modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                        ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                params.preferredRefreshRate = if (enableHighRefreshRate) {
                    // Same fix as the R+ branch above: 0f is "no preference", not "fastest".
                    window.windowManager.defaultDisplay.supportedRefreshRates
                        .maxOrNull()
                        ?: 0f
                } else {
                    60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }

        // The seed for the whole app's ColorScheme. Static on purpose: it used to be
        // re-derived from every track's artwork, which built a NEW ColorScheme object per
        // song change. That invalidates MaterialTheme, and every composable in the app
        // reading AppTheme.colorScheme.* recomposes with it -- a full-tree recompose
        // on each track transition. Per-artwork colour still exists where it is local and
        // cheap (HeroArtwork's tints); it no longer reseeds the global theme.
        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val themeColor = Color(selectedThemeColorInt)

        // A guest in an owner-controlled room has every "play this" refused at
        // PlayerConnection. Refusing silently reads as the app being broken, and the
        // taps come from anywhere in the app (Home tiles, search, menus), so the
        // explanation belongs here rather than on each screen.
        LaunchedEffect(playerConnection) {
            val connection = playerConnection ?: return@LaunchedEffect
            connection.playbackBlockedByRoom.collect { blocked ->
                if (!blocked) return@collect
                Toast.makeText(
                    this@MainActivity,
                    this@MainActivity.getString(R.string.listen_together_host_controls),
                    Toast.LENGTH_SHORT,
                ).show()
                connection.playbackBlockedByRoom.value = false
            }
        }

        vivimusicTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            val (appBackgroundColorInt) = rememberPreference(AppBackgroundColorKey, defaultValue = 0)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    // The base canvas behind every screen, tab-view's side panel and
                    // main content pane included — HomeImageBackground and similar
                    // per-screen overlays paint on top of this, but this is what shows
                    // through wherever they don't, so it must follow the user's global
                    // background color too, not just pureBlack vs the raw theme surface.
                    .background(
                        when {
                            appBackgroundColorInt != 0 -> Color(appBackgroundColorInt)
                            pureBlack -> Color.Black
                            else -> AppTheme.colorScheme.surface
                        }
                    )
            ) {
                val focusManager = LocalFocusManager.current
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()

                // Nominates the artwork morph's source from the destination being opened,
                // rather than from each of the dozens of tap handlers that can open one.
                // The id in the route arguments IS the id of the tile that was tapped, so
                // one listener covers every screen -- including ones added later, which a
                // per-call-site approach would keep missing.
                //
                // If the tile is not on screen (deep link, restored back stack, or the
                // same id opened from somewhere with no artwork) nothing claims the source
                // key and the destination simply cross-fades in, which is the pre-existing
                // behaviour.
                DisposableEffect(navController) {
                    // -1 sentinel: the very first destination change (app launch) is
                    // neither a push nor a pop, and comparing against an unset size would
                    // otherwise read as a pop (0 < -1 is false, so it's actually safe
                    // either way, but the explicit sentinel makes that not an accident).
                    var previousBackStackSize = -1
                    @SuppressLint("RestrictedApi")
                    val listener = NavController.OnDestinationChangedListener { controller, _, args ->
                        val currentSize = controller.currentBackStack.value.size
                        val isPop = previousBackStackSize != -1 && currentSize < previousBackStackSize
                        previousBackStackSize = currentSize
                        markMorphDirection(isPop)

                        val thumbnailUrl = markSharedArtworkSource(args.morphArtworkId())
                        // Extract the hero tint from the tile's own artwork now, while the
                        // morph is still playing, rather than when the destination first
                        // composes -- that is what makes the colour already be there as the
                        // screen finishes growing instead of washing in a beat later.
                        prewarmHeroTint(this@MainActivity, thumbnailUrl)
                        prewarmMeshPalette(this@MainActivity, thumbnailUrl)
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose { navController.removeOnDestinationChangedListener(listener) }
                }

                val homeViewModel: HomeViewModel = hiltViewModel()
                // Pre-warm HistoryViewModel at Activity scope so history data loads
                // in background immediately â€” zero lag when user taps the history icon
                hiltViewModel<HistoryViewModel>()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
                // Read here rather than at its old spot further down: the tab row now
                // depends on it, and a second rememberPreference for the same key would
                // mean a second DataStore collector for one boolean.
                val (localOnlyMode) = rememberPreference(LocalOnlyModeKey, false)
                val navigationItems = remember(listenTogetherInTopBar, localOnlyMode) {
                    // Local-only mode adds the flat all-songs tab after Home.
                    val screens = Screens.mainScreens(localOnlyMode)
                    if (listenTogetherInTopBar) {
                        screens.filter { it != Screens.ListenTogether }
                    } else {
                        screens
                    }
                }
                val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                val (appleMusicUi) = rememberPreference(AppleMusicUiKey, defaultValue = true)
                // The Explore (discovery) tab is exclusive to the floating (iOS-style)
                // tab bar — the classic nav bar keeps discovery off the row and
                // settings behind the top bar icon.
                val floatingNavigationItems = remember(navigationItems) {
                    navigationItems + Screens.Explore
                }
                val floatingNavBarScrollConnection = rememberFloatingTabBarScrollConnection()
                val (liquidGlassGlobalEnabled) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = true)
                val (liquidGlassVibrancy) = rememberPreference(LiquidGlassVibrancyKey, defaultValue = 2.0f)
                val (liquidGlassBlurRadius) = rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 3f)
                val (liquidGlassLensHeight) = rememberPreference(LiquidGlassLensHeightKey, defaultValue = 0.60f)
                val (liquidGlassLensAmount) = rememberPreference(LiquidGlassLensAmountKey, defaultValue = 0.69f)
                val (liquidGlassChromaticAberration) = rememberPreference(LiquidGlassChromaticAberrationKey, defaultValue = false)
                val (liquidGlassDepthEffect) = rememberPreference(LiquidGlassDepthEffectKey, defaultValue = false)
                // 0 (fully transparent, unreachable from the color picker) marks the
                // theme-adaptive default tint.
                // 0 = theme-adaptive tint (a lighter frosted grey) rather than a
                // fixed dark chip â€” the dark default read as a near-black bar (and
                // an invisible button surface over a dark hero, e.g. the playlist
                // action buttons).
                val (liquidGlassSurfaceTintColorInt) = rememberPreference(LiquidGlassSurfaceTintColorKey, defaultValue = 0)
                val (liquidGlassSurfaceOpacity) = rememberPreference(LiquidGlassSurfaceOpacityKey, defaultValue = 0.5f)
                val liquidGlassStyle by rememberEnumPreference(LiquidGlassStyleKey, defaultValue = GlassStyle.LIQUID)
                val (liquidGlassPuckColorInt) = rememberPreference(LiquidGlassPuckColorKey, defaultValue = 0)
                val (liquidGlassPuckOpacity) = rememberPreference(LiquidGlassPuckOpacityKey, defaultValue = 0.8f)
                val (liquidGlassHighlightColorInt) = rememberPreference(LiquidGlassHighlightColorKey, defaultValue = 0)
                val (liquidGlassHighlightOpacity) = rememberPreference(LiquidGlassHighlightOpacityKey, defaultValue = 0.33f)
                // 0 (fully transparent, unreachable from the color picker) marks the
                // theme-adaptive default, same convention as the surface tint above.
                // A hardcoded white default left the mini player and nav bar text
                // invisible against light-mode glass.
                val (liquidGlassTextColorInt) = rememberPreference(LiquidGlassTextColorKey, defaultValue = 0)
                val (liquidGlassAdaptiveContrast) = rememberPreference(
                    LiquidGlassAdaptiveContrastKey,
                    defaultValue = true,
                )
                // Theme-only fallback. Correct for an untinted pill, but blind to the
                // tint the user actually picked â€” a dark tint at high opacity reads
                // dark even in light mode, which is where white-on-glass disappears.
                val themeGlassTextColor = if (useDarkTheme) Color.White else Color(0xFF1A1A1A)
                val (liquidGlassMiniPlayerEnabled) = rememberPreference(LiquidGlassMiniPlayerEnabledKey, defaultValue = true)
                val (liquidGlassNavBarEnabled) = rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = true)
                val (liquidGlassSidePanelEnabled) = rememberPreference(LiquidGlassSidePanelEnabledKey, defaultValue = true)
                val (liquidGlassSidePanelVibrancy) = rememberPreference(LiquidGlassSidePanelVibrancyKey, defaultValue = 1.2f)
                val (liquidGlassSidePanelBlurRadius) = rememberPreference(LiquidGlassSidePanelBlurRadiusKey, defaultValue = 2f)
                val (liquidGlassSidePanelLensHeight) = rememberPreference(LiquidGlassSidePanelLensHeightKey, defaultValue = 0.4f)
                val (liquidGlassSidePanelLensAmount) = rememberPreference(LiquidGlassSidePanelLensAmountKey, defaultValue = 0.6f)
                val (liquidGlassSidePanelColorInt) = rememberPreference(LiquidGlassSidePanelColorKey, defaultValue = 0)
                val (liquidGlassSidePanelSurfaceOpacity) = rememberPreference(LiquidGlassSidePanelSurfaceOpacityKey, defaultValue = 0.5f)
                val (liquidGlassSidePanelTextColorInt) = rememberPreference(LiquidGlassSidePanelTextColorKey, defaultValue = 0)

                // Content colour derived from what the pill actually composites to â€”
                // the surface behind it blended with the chosen tint at its opacity â€”
                // rather than from the theme alone. Keeps chrome legible for any
                // tint/opacity/theme combination instead of only the untinted ones.
                // Per-theme glass defaults (spec Q1-Q4): a 0 sentinel means
                // "untouched" and resolves to the dark/light spec set, so
                // switching theme flips the glass automatically. Picking a
                // custom color unlocks the manual opacity sliders instead.
                val resolvedGlassTint = if (liquidGlassSurfaceTintColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultSurfaceDark else GlassTintTokens.DefaultSurfaceLight
                } else {
                    Color(liquidGlassSurfaceTintColorInt)
                }
                val resolvedGlassOpacity = if (liquidGlassSurfaceTintColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultSurfaceOpacityDark else GlassTintTokens.DefaultSurfaceOpacityLight
                } else {
                    liquidGlassSurfaceOpacity
                }
                val resolvedPuckTint = if (liquidGlassPuckColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultPuckDark else GlassTintTokens.DefaultPuckLight
                } else {
                    Color(liquidGlassPuckColorInt)
                }
                val resolvedPuckOpacity = if (liquidGlassPuckColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultPuckOpacityDark else GlassTintTokens.DefaultPuckOpacityLight
                } else {
                    liquidGlassPuckOpacity
                }
                val resolvedHighlightTint = if (liquidGlassHighlightColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultHighlightDark else GlassTintTokens.DefaultHighlightLight
                } else {
                    Color(liquidGlassHighlightColorInt)
                }
                val resolvedHighlightOpacity = if (liquidGlassHighlightColorInt == 0) {
                    if (useDarkTheme) GlassTintTokens.DefaultHighlightOpacityDark else GlassTintTokens.DefaultHighlightOpacityLight
                } else {
                    liquidGlassHighlightOpacity
                }
                val resolvedSidePanelTint = if (liquidGlassSidePanelColorInt == 0) {
                    Color.Unspecified
                } else {
                    Color(liquidGlassSidePanelColorInt)
                }
                val glassSurfaceBehind = AppTheme.colorScheme.surface
                val adaptiveGlassTextColor = if (liquidGlassAdaptiveContrast) {
                    glassContentColorFor(glassSurfaceBehind, resolvedGlassTint, resolvedGlassOpacity)
                } else {
                    themeGlassTextColor
                }
                val adaptiveSidePanelTextColor = if (liquidGlassAdaptiveContrast) {
                    glassContentColorFor(
                        glassSurfaceBehind,
                        resolvedSidePanelTint,
                        liquidGlassSidePanelSurfaceOpacity,
                    )
                } else {
                    themeGlassTextColor
                }

                val glassEffectConfig = remember(
                    liquidGlassGlobalEnabled, liquidGlassVibrancy, liquidGlassBlurRadius,
                    liquidGlassLensHeight, liquidGlassLensAmount, liquidGlassChromaticAberration,
                    liquidGlassDepthEffect, liquidGlassSurfaceTintColorInt,
                    liquidGlassSurfaceOpacity, liquidGlassTextColorInt,
                    liquidGlassHighlightColorInt, liquidGlassHighlightOpacity, liquidGlassStyle,
                    liquidGlassPuckColorInt, liquidGlassPuckOpacity,
                    liquidGlassMiniPlayerEnabled, liquidGlassNavBarEnabled, liquidGlassSidePanelEnabled,
                    liquidGlassSidePanelVibrancy, liquidGlassSidePanelBlurRadius,
                    liquidGlassSidePanelLensHeight, liquidGlassSidePanelLensAmount,
                    liquidGlassSidePanelColorInt, liquidGlassSidePanelSurfaceOpacity, liquidGlassSidePanelTextColorInt,
                    adaptiveGlassTextColor, adaptiveSidePanelTextColor, useDarkTheme,
                ) {
                    // The sliders in Glass settings are always the source of truth: the
                    // Apple Music UI toggle just writes a starting preset into them once
                    // when switched on, so the user can keep tuning from there afterward.
                    GlassEffectConfig(
                        // The glass look is part of the floating nav bar experience, so it
                        // only activates when that bar is enabled too.
                        globalEnabled = liquidGlassGlobalEnabled,
                        vibrancy = liquidGlassVibrancy,
                        blurRadius = liquidGlassBlurRadius,
                        lensHeight = liquidGlassLensHeight,
                        lensAmount = liquidGlassLensAmount,
                        chromaticAberration = liquidGlassChromaticAberration,
                        depthEffect = liquidGlassDepthEffect,
                        surfaceTintColor = resolvedGlassTint,
                        surfaceOpacity = resolvedGlassOpacity,
                        highlightColor = resolvedHighlightTint,
                        highlightOpacity = resolvedHighlightOpacity,
                        style = liquidGlassStyle,
                        puckColor = resolvedPuckTint,
                        puckOpacity = resolvedPuckOpacity,
                        textColor = if (liquidGlassTextColorInt == 0) adaptiveGlassTextColor else Color(liquidGlassTextColorInt),
                        miniPlayerEnabled = liquidGlassMiniPlayerEnabled,
                        navBarEnabled = liquidGlassNavBarEnabled,
                        sidePanelEnabled = liquidGlassSidePanelEnabled,
                        sidePanelVibrancy = liquidGlassSidePanelVibrancy,
                        sidePanelBlurRadius = liquidGlassSidePanelBlurRadius,
                        sidePanelLensHeight = liquidGlassSidePanelLensHeight,
                        sidePanelLensAmount = liquidGlassSidePanelLensAmount,
                        sidePanelColor = if (liquidGlassSidePanelColorInt == 0) Color.Unspecified else Color(liquidGlassSidePanelColorInt),
                        sidePanelSurfaceOpacity = liquidGlassSidePanelSurfaceOpacity,
                        sidePanelTextColor = if (liquidGlassSidePanelTextColorInt == 0) adaptiveSidePanelTextColor else Color(liquidGlassSidePanelTextColorInt),
                    )
                }
                // API level + low-RAM gate, hoisted so the backdrop capture below can
                // be skipped entirely on devices that can never render glass.
                val glassAllowed = isGlassAllowed()

                // One blurred copy of the screen, shared by every glass surface, instead of
                // each of them saturating and blurring its own slice. Home carries eight
                // surfaces; at ~5 offscreen passes each that was the frame's whole budget.
                val glassPoolBackdrop = rememberGlassPool()
                val poolSaturation = glassSaturation(glassEffectConfig.vibrancy)
                val poolBlurPx = with(LocalDensity.current) {
                    glassEffectConfig.blurRadius.dp.toPx()
                } * GlassPoolScale
                val poolEffects: BackdropEffectScope.() -> Unit =
                    remember(poolSaturation, poolBlurPx) {
                        {
                            if (poolSaturation != 1f) colorControls(saturation = poolSaturation)
                            if (poolBlurPx > 0f) blur(poolBlurPx)
                        }
                    }
                val pooledGlass = remember(
                    glassPoolBackdrop,
                    glassEffectConfig.blurRadius,
                    glassEffectConfig.vibrancy,
                    glassAllowed,
                    glassEffectConfig.anyComponentEnabled,
                ) {
                    if (USE_SHARED_GLASS_POOL && glassAllowed && glassEffectConfig.anyComponentEnabled) {
                        PooledGlass(
                            backdrop = glassPoolBackdrop,
                            blurRadiusDp = glassEffectConfig.blurRadius,
                            vibrancy = glassEffectConfig.vibrancy,
                        )
                    } else {
                        null
                    }
                }

                val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                val defaultOpenTab = remember {
                    dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                }
                val tabOpenedFromShortcut = remember {
                    when (intent?.action) {
                        ACTION_SEARCH -> NavigationTab.LIBRARY
                        ACTION_LIBRARY -> NavigationTab.SEARCH
                        else -> null
                    }
                }

                // Home/Search/Library/ListenTogether/Settings render as pages of one
                // pager (see MainTabsPager.kt) instead of five separate NavHost
                // destinations, so switching between them is a pager scroll rather
                // than a destination swap -- no AnimatedContent transition, no
                // backdrop re-record. The initial page still honors a launch
                // shortcut / the user's default-open-tab preference exactly like the
                // old startDestination selection did.
                // Search is not a NavHost destination -- opening on it means opening
                // Home and raising the search overlay over it below.
                val openSearchOnLaunch = remember {
                    (tabOpenedFromShortcut ?: defaultOpenTab) == NavigationTab.SEARCH
                }
                // NavHost's startDestination is read once at first composition, so this
                // is where "open on a specific tab" (launch shortcut / default-open-tab
                // preference) is actually decided -- see the NavHost call below.
                val initialMainTabRoute = remember {
                    when (tabOpenedFromShortcut ?: defaultOpenTab) {
                        NavigationTab.HOME, NavigationTab.SEARCH -> Screens.Home.route
                        NavigationTab.LIBRARY -> Screens.Library.route
                    }
                }

                val topLevelScreens = remember {
                    listOf(
                        Screens.Home.route,
                        Screens.Library.route,
                        Screens.ListenTogether.route,
                        "settings",
                    )
                }

                val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue())
                }
                // Whether the nav bar's search field currently owns the keyboard â€” the
                // one thing that isn't route-derived (search_input/search/{query} don't
                // change across the first-tap/second-tap boundary). Reset below whenever
                // navigation leaves both search routes.
                var searchKeyboardActive by rememberSaveable { mutableStateOf(false) }
                var storedSearchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
                // Local-only mode pins search to the on-device library; the stored
                // preference is left alone so it returns when the mode is turned off.
                val searchSource = if (localOnlyMode) SearchSource.LOCAL else storedSearchSource
                val searchFocusRequester = remember { FocusRequester() }
                // Non-null while entering/exiting search overrides the route-derived
                // visual state, so the shrink/expand animation plays out before the
                // actual navigation call lands (see enterSearch/exitSearch below).
                var searchVisualOverride by remember { mutableStateOf<Boolean?>(null) }

                val onSearch: (String) -> Unit = remember(localOnlyMode) {
                    { searchQuery ->
                        if (searchQuery.isNotEmpty()) {
                            // search/{query} is the YouTube results screen. In local-only
                            // mode the results are already on screen (search_input renders
                            // LocalSearchScreen live), so submitting just records history.
                            if (!localOnlyMode) navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}") {
                                // No launchSingleTop: it compares destination id, not
                                // resolved args, so re-submitting a new query while
                                // already on search/{oldQuery} could get silently
                                // treated as "already there" and dropped. popUpTo
                                // below still prevents stacking a new entry per edit.
                                popUpTo("search/{query}") { inclusive = true }
                            }

                            if (dataStore[PauseSearchHistoryKey] != true) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    database.query {
                                        insert(SearchHistory(query = searchQuery))
                                    }
                                }
                            }
                        }
                    }
                }

                // Use derivedStateOf to avoid unnecessary recompositions
                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }
                LaunchedEffect(currentRoute) {
                    Timber.tag("Navigation").d("route -> $currentRoute")
                }

                // Search is a plain NavHost destination again (Screens.Search.route),
                // not an overlay layered above the tabs. It was made an overlay to
                // dodge the main-tabs pager (search would have had to be a page,
                // sliding through Library on the way in/out); the pager itself was
                // reverted back to plain destinations and search was left behind as
                // an overlay, which is what caused the stale-backdrop/hidden-results/
                // hidden-suggestion-navigation bugs (a tap inside search pushed a
                // real destination, but the overlay Box stayed on top holding its
                // last frame, sampled by the nav bar's glass and hiding the pushed
                // screen). Being a real destination again fixes all of it for free --
                // the same NavHost/backdrop machinery every other screen already uses.
                LaunchedEffect(Unit) {
                    if (openSearchOnLaunch) {
                        navController.navigate(Screens.Search.route) { launchSingleTop = true }
                    }
                }

                val effectiveRoute = currentRoute

                val inSearchScreen by remember {
                    derivedStateOf {
                        currentRoute?.startsWith("search/") == true ||
                            currentRoute == Screens.Search.route
                    }
                }
                val inSearchInputScreen by remember {
                    derivedStateOf { currentRoute == Screens.Search.route }
                }
                LaunchedEffect(inSearchScreen) {
                    if (!inSearchScreen) searchKeyboardActive = false
                }
                // The floating nav bar keeps Settings as one of its own tabs (see
                // floatingNavigationItems above), so it should stay visible there too â€”
                // only the classic nav bar treats Settings as a top-bar-only destination.
                val navigationItemRoutes = remember(floatingNavigationItems) {
                    floatingNavigationItems.map { it.route }.toSet()
                }

                val isKeyboardOpen = WindowInsets.isImeVisible
                val shouldShowNavigationBar = remember(currentRoute) {
                    when {
                        // By design: nav bar shows only at Settings root ("settings"
                        // bare route); every drilled-in sub-page ("settings/...") is a
                        // focused detail/editor context and hides it, same as the other
                        // full-screen flows below. Reaching Home from a sub-page is via
                        // one back-tap to root (where the bar reappears) or the back
                        // arrow's long-press (backToMain()) shortcut.
                        currentRoute?.startsWith("settings/") == true -> false
                        currentRoute in setOf("login", "channel_picker", "equalizer", "wrapped", "update", "listen_together/chat") -> false
                        else -> true
                    }
                }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                // The floating liquid-glass nav bar is a centered bottom pill that
                // works the same in landscape, so don't swap it for the side rail
                // there â€” only the classic nav bar falls back to the rail.
                //
                // "Tab view" forces the same side layout on any device, including a
                // phone in portrait, so the tablet sidebar can be tried out without
                // a tablet.
                val (forceTabletLayout) = rememberPreference(ForceTabletLayoutKey, defaultValue = false)
                // A real tablet gets the side bar on its own; the toggle only exists
                // to force it onto a device that wouldn't otherwise qualify.
                val isWideScreen = configuration.containerDpSize.width >= TabletWidthThreshold
                val showRail = forceTabletLayout || isWideScreen
                val (sideBarCollapsed, onSideBarCollapsedChange) = rememberPreference(SideBarCollapsedKey, defaultValue = false)
                val sideBarContentInset by animateDpAsState(
                    targetValue = if (sideBarCollapsed) SideBarCollapsedWidth + SideBarMargin * 2 else SideBarContentInset,
                    animationSpec = spring(0.9f, 400f),
                    label = "sideBarContentInset",
                )

                val navPadding = if (shouldShowNavigationBar && !showRail) {
                    if (slimNav) SlimNavBarHeight else NavigationBarHeight
                } else {
                    0.dp
                }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerMediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
                    ?: remember { mutableStateOf(null) }
                // With the floating nav bar the mini player docks into the tab bar as an
                // accessory, and in tab view it docks into the sidebar footer, so in both
                // cases the sheet's collapsed state coincides with dismissed and the
                // standalone collapsed mini player never shows. Without this, turning tab
                // view on swapped the docked accessory for the sheet's own mini player,
                // which is a different component and reads as a different app.
                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    // The mini player always docks into the tab bar (or, in tab view, the
                    // sidebar footer), so the sheet's collapsed state always coincides with
                    // dismissed and the standalone collapsed mini player never shows.
                    collapsedBound = 0.dp,
                    expandedBound = maxHeight,
                )

                // Both ends of the mini-to-full morph are recorded into GraphicsLayers
                // owned here, above both the player and the nav bar the pill docks into --
                // remembering them any lower would release them mid-gesture.
                InstallPlayerMorphLayers()

                // See the zIndex on the player below.
                val playerAboveBars by remember(playerBottomSheetState) {
                    derivedStateOf { playerBottomSheetState.progress > 0f }
                }

                // Only reserve space for the docked player accessory on screens where the
                // floating tab bar is actually visible; other screens (e.g. settings) get
                // the full height. In search mode the mini player still docks (now part of
                // the search-expanded/search-inline chrome) â€” it only drops out once the
                // keyboard takes over the bar entirely.
                val hasDockedPlayerAccessory =
                    playerMediaMetadata != null && !showRail && shouldShowNavigationBar &&
                        (!inSearchScreen || !searchKeyboardActive)

                // Does anything ON THIS SCREEN actually sample the app backdrop?
                //
                // `anyComponentEnabled` only asks whether a component is enabled ANYWHERE in
                // the app, so with the nav bar's glass on -- the default -- every screen paid
                // for the full-screen capture, including screens with no nav bar and no mini
                // player on them at all. Measured on a Galaxy M34: skipping the capture takes
                // a Home scroll from p50 105ms to 25ms, so this is worth being precise about.
                //
                // Each clause pairs "is this component's glass enabled" with "is that
                // component on screen right now". The player's own glass only needs the
                // backdrop once the sheet has left its collapsed anchor -- isCollapsed is
                // derived state that flips once per open and once per close, NOT per frame,
                // so it is safe to read here (unlike `progress`).
                val backdropHasConsumer =
                    (glassEffectConfig.navBarEnabled && shouldShowNavigationBar && !showRail) ||
                        (glassEffectConfig.sidePanelEnabled && showRail) ||
                        (glassEffectConfig.miniPlayerEnabled && hasDockedPlayerAccessory) ||
                        // The full player's own glass has no on/off switch any more (the old
                        // "Glass Player" toggle never actually gated it -- see GlassEffect.kt),
                        // so it is an unconditional backdrop consumer whenever the sheet isn't
                        // collapsed, same as before minus the always-true config check.
                        !playerBottomSheetState.isCollapsed

                val playerAwareWindowInsets = remember(
                    bottomInset,
                    shouldShowNavigationBar,
                    playerBottomSheetState.isDismissed,
                    showRail,
                    hasDockedPlayerAccessory,
                    sideBarContentInset,
                    inSearchInputScreen,
                ) {
                    var bottom = bottomInset
                    if (shouldShowNavigationBar && !showRail) {
                        bottom += NavigationBarHeight
                    }
                    if (!playerBottomSheetState.isDismissed && !inSearchInputScreen) bottom += MiniPlayerHeight
                    if (hasDockedPlayerAccessory) bottom += DockedAccessoryHeight
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        // Space for the floating side bar, carried the same way the
                        // bottom bar's space is: screens spend this as list content
                        // padding, so rows start clear of the panel but the list
                        // still spans the full width and scrolls under its glass.
                        .add(
                            WindowInsets(left = if (showRail) sideBarContentInset else 0.dp)
                        )
                }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    }
                )

                val topAppBarScrollBehavior = appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                // Navigation tracking. Keyed on effectiveRoute too, not just
                // navBackStackEntry -- a pager tab switch (Home/Search/Library/
                // ListenTogether/Settings) no longer changes the real back stack
                // entry at all, so navBackStackEntry alone would miss it entirely.
                // Still keyed on navBackStackEntry as well: resubmitting a new
                // search query pushes a new entry on the SAME destination template
                // ("search/{query}"), which would not otherwise register as a change.
                LaunchedEffect(navBackStackEntry, effectiveRoute) {
                    // Only the results route (search/{query}) carries a query arg;
                    // the search_input landing does not â€” guard against a null arg
                    // so tapping search never NPEs.
                    val rawQuery = navBackStackEntry?.arguments?.getString("query")
                    if (inSearchScreen && rawQuery != null) {
                        val searchQuery = withContext(Dispatchers.IO) {
                            try {
                                URLDecoder.decode(rawQuery, "UTF-8")
                            } catch (e: IllegalArgumentException) {
                                rawQuery
                            }
                        }
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length)
                            )
                        )
                    } else if (navigationItems.fastAny { it.route == effectiveRoute }) {
                        onQueryChange(TextFieldValue())
                    }

                    // Reset scroll behavior for main navigation items
                    if (navigationItems.fastAny { it.route == effectiveRoute }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    // Track previous tab for animations
                    effectiveRoute?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(playerConnection) {
                    val player = playerConnection?.player ?: return@LaunchedEffect
                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                }

                DisposableEffect(playerConnection, playerBottomSheetState) {
                    val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem != null &&
                                playerBottomSheetState.isDismissed
                            ) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                // Keyed on effectiveRoute (not navBackStackEntry) so this reacts to a
                // pager tab switch too -- the real NavHost entry no longer changes
                // when switching between Home/Search/Library/ListenTogether/Settings.
                LaunchedEffect(effectiveRoute, listenTogetherInTopBar, showRail) {
                    val isListenTogetherScreen = effectiveRoute == Screens.ListenTogether.route ||
                        currentRoute == "listen_together_from_topbar"
                    shouldShowTopBar = !showRail &&
                        effectiveRoute in topLevelScreens &&
                        effectiveRoute != "settings" &&
                        !(isListenTogetherScreen && listenTogetherInTopBar)
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                var showSettingDialoge by remember { mutableStateOf(false) }
                val (enableSettingsPopup) = rememberPreference(EnableSettingsPopupKey, defaultValue = false)

                val ringtoneViewModel: com.convx.music.ui.screens.settings.RingtoneViewModel = viewModel()
                val ringtoneUiState by ringtoneViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { intent ->
                        handleDeepLinkIntent(intent, navController)
                    }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitleRes = remember(effectiveRoute) {
                    when (effectiveRoute) {
                        Screens.Home.route -> com.convx.music.R.string.music
                        Screens.Search.route -> com.convx.music.R.string.search
                        Screens.Library.route -> com.convx.music.R.string.filter_library
                        Screens.ListenTogether.route -> com.convx.music.R.string.together
                        Screens.Explore.route -> com.convx.music.R.string.tab_explore
                        else -> null
                    }
                }



                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
                val (historyButtonEnabled) = rememberPreference(ShowHistoryButtonKey, defaultValue = true)
                val (showStatsButton) = rememberPreference(ShowStatsButtonKey, defaultValue = true)
                val showHistoryButton = remember(pauseListenHistory, eventCount, historyButtonEnabled) {
                    historyButtonEnabled && !(pauseListenHistory && eventCount == 0)
                }

                val baseBg = if (pureBlack) Color.Black else AppTheme.colorScheme.surfaceContainer

                // Remember the draw lambda so rememberLayerBackdrop (which keys on it)
                // isn't recreated on every app recomposition, forcing a full backdrop
                // re-record.
                val appBackdrop = rememberLayerBackdrop(
                    onDraw = remember(baseBg) {
                        val bg = baseBg
                        {
                            drawRect(bg)
                            drawContent()
                        }
                    }
                )

                // While the user is scrolling, stop re-recording the backdrop source.
                // layer.record { drawContent() } cannot reuse unchanged child RenderNodes,
                // so it re-issues the entire screen every frame â€” measured at 47ms of the
                // ~57ms Home scroll frame, against 1.9ms for Compose's ordinary draw path.
                // The content keeps scrolling live; only the blur sampled by the nav/top
                // bar holds its last capture until the gesture settles.
                // The frozen flag is a PLAIN timestamp, deliberately not snapshot state: the
                // provider below is called during the draw phase, so a snapshot read there
                // registers a draw dependency and every write re-invalidates the frame. A
                // first attempt did exactly that and left the app redrawing forever after
                // any scroll (266 frames/7s at rest). Same trap as LayerBackdrop.contentVersion.
                val backdropFreeze = rememberBackdropFreeze()
                val backdropFreezeConnection = backdropFreeze.connection
                // A plain nav transition (slide+fade between two screens) is neither a
                // scroll nor a fling, so backdropFreeze never caught it — the backdrop
                // re-recorded every frame of every navigation for the whole ~200ms
                // transition, at the same per-frame cost already measured and fixed for
                // scroll. OR'd in here rather than folded into BackdropFreeze itself,
                // which many other screens also use for their own local backdrops.
                val navTransitionFreeze = rememberNavTransitionFreeze(currentRoute)
                // Home/Library/Settings are real NavHost destinations again, so a tab
                // switch IS a route change and navTransitionFreeze above already covers
                // it -- the separate pager-motion freeze this used to need doesn't
                // apply once there is no pager motion to miss.
                // Remembered so the lambda keeps its identity across recompositions.
                // LayerBackdropElement compares `frozen` in equals(), so a fresh lambda
                // every recomposition counted as a modifier change and forced an
                // invalidateDraw() -- harmless when only the app backdrop read it, but it
                // now also feeds the tab bar's backdrop through LocalAppBackdropFrozen.
                // Both freezes are stable objects; navTransitionFreeze is itself keyed on
                // the current route, so it changes exactly when it should.
                val backdropFrozenProvider = remember(
                    backdropFreeze,
                    navTransitionFreeze,
                    floatingNavBarScrollConnection,
                ) {
                    {
                        backdropFreeze.frozen() ||
                            navTransitionFreeze.frozen() ||
                            // This is the same clock that visibly drives the bar's
                            // collapse/expand, so it is guaranteed to observe the
                            // nested scroll stream. Sharing it prevents the app
                            // backdrop (and therefore GlassPool.contentVersion) from
                            // re-recording while list pixels are moving.
                            floatingNavBarScrollConnection.isScrolling()
                    }
                }

                // One provider replaces Android's stretch/glow edge effect with the
                // iOS rubber-band for every scroll container in the app.
                val iosOverscroll by rememberPreference(IosOverscrollKey, defaultValue = true)
                val iosOverscrollFactory = rememberIosOverscrollFactory()

                // Entering/exiting search: hold the nav bar's visual state at the
                // target (search-expanded / normal) for one animation beat before the
                // actual navigation call, so the shrink/expand-to-pill animation plays
                // out first instead of racing the route's own screen transition.
                val enterSearch: () -> Unit = remember(navController, coroutineScope) {
                    {
                        searchVisualOverride = true
                        coroutineScope.launch {
                            delay(SearchNavTransitionDelayMs)
                            // Search opens over whatever tab is showing. Drilled into a
                            // detail screen, pop back to the tab root first so search
                            // does not stack on top of an album page the user can no
                            // longer see the back button for.
                            if (navController.currentDestination?.route !in TabRootRoutes) {
                                navController.popBackStack(navController.graph.startDestinationId, inclusive = false)
                            }
                            navController.navigate(Screens.Search.route) { launchSingleTop = true }
                            searchVisualOverride = null
                        }
                    }
                }
                val exitSearch: () -> Unit = remember(navController, coroutineScope) {
                    {
                        searchKeyboardActive = false
                        searchVisualOverride = false
                        coroutineScope.launch {
                            delay(SearchNavTransitionDelayMs)
                            // Pop the WHOLE search flow, not a single level. Searching
                            // pushes search_input and then search/{query} on top of it,
                            // so navigateUp() from a results screen would only land back
                            // on the input screen. Cancelling search must return to
                            // whatever preceded it.
                            if (!navController.popBackStack(Screens.Search.route, inclusive = true)) {
                                navController.navigateUp()
                            }
                            searchVisualOverride = null
                        }
                    }
                }

                // The nav bar (rendered outside/above search_input and search/{query} in
                // the tree below) owns the actual search text field now â€” both screens
                // just read this to filter/display results.
                val navSearchState = NavSearchState(
                    visualActive = searchVisualOverride ?: inSearchScreen,
                    keyboardActive = searchKeyboardActive,
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSearch,
                    searchSource = searchSource,
                    onToggleSource = {
                        storedSearchSource =
                            if (storedSearchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                    },
                    canToggleSource = !localOnlyMode,
                    onTapSearchIcon = enterSearch,
                    onTapBar = {
                        if (inSearchScreen && !inSearchInputScreen) {
                            // Tapping the bar again from a results screen (search/{query})
                            // pops back to the input screen and reopens the keyboard
                            // instead of trying to resubmit in place.
                            navController.popBackStack(Screens.Search.route, inclusive = false)
                        }
                        searchKeyboardActive = true
                    },
                    onExit = exitSearch,
                    onCloseKeyboard = { searchKeyboardActive = false },
                    focusRequester = searchFocusRequester,
                )

                // Counts the sheets currently on screen, so the presentation below holds
                // until the last one closes rather than the first. Every sheet registers
                // itself through the shared presenter -- see PresentingSheetEffect.
                val sheetPresentationState = remember { SheetPresentationState() }

                // See HomeTopBarState.kt — Home writes into this from its own
                // scroll state, the top bar's leading logo reads it.
                val homeTopBarLogoProgress = remember { mutableFloatStateOf(0f) }

                CompositionLocalProvider(
                    LocalOverscrollFactory provides
                        if (iosOverscroll) iosOverscrollFactory else LocalOverscrollFactory.current,
                    LocalDatabase provides database,
                    LocalNavSearchState provides navSearchState,
                    LocalSheetPresentation provides sheetPresentationState,
                    // onSurface already carries the accent-contrast treatment (see
                    // ColorScheme.accentText), so pure black must not force plain white
                    // back over it.
                    LocalContentColor provides AppTheme.colorScheme.onSurface,
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    // Temporarily disabled — see the initialize() comment in onCreate. Every
                    // current entry point (PlayerMenu's ListenTogetherDialog, ListenTogetherScreen)
                    // already has a null-manager fallback, so this alone turns the whole feature off.
                    LocalListenTogetherManager provides null,
                    LocalGlassEffectConfig provides glassEffectConfig,
                    LocalTabView provides showRail,
                    LocalAppBackdrop provides appBackdrop,
                    // Same backdrop, for the ios/ component library's glass surfaces.
                    LocalIosBackdrop provides appBackdrop,
                    LocalAppBackdropFrozen provides backdropFrozenProvider,
                    LocalRingtoneViewModel provides ringtoneViewModel,
                    // Read once here instead of inside every list row and grid tile —
                    // see ItemPrefs for what that was costing on scroll.
                    LocalItemPrefs provides rememberItemPrefs(),
                    // One collector for the whole app instead of one per list row —
                    // see LocalDownloads.
                    LocalDownloads provides downloadUtil.downloads.collectAsState().value,
                    // Hand-off between Home's own scroll state and the top bar's
                    // logo, which live in different parts of the composition — see
                    // HomeTopBarState.kt.
                    LocalHomeTopBarLogoProgress provides homeTopBarLogoProgress,
                ) {

                    // The screen a sheet is covering shrinks back behind it, the way
                    // iOS presents one card over another.
                    // Menus are popups now and leave the page where it is (iOS 27); only the
                    // sheet-style pages still present as a card.
                    val menuPresentation = rememberSheetPresentation(
                        LocalBottomSheetPageState.current.isVisible,
                    )

                    // The Scaffold shrinks only for a menu. It used to shrink for the player
                    // too (the iOS 18 card stack); iOS 27's Now Playing grows over a page that
                    // stays put (designRefrence/threadsdownloader.com_0805d3.mp4), and the
                    // user asked for the shrink to go.
                    // The player layer ONLY shrinks for a menu (it is the card in front).
                    val scaffoldPresentation = { menuPresentation }
                    val playerLayerPresentation = { menuPresentation }

                    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                    // Moved up from Scaffold.bottomBar to be shared with the new bottom layer
                    val onNavItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState, enterSearch) {
                        var lastNavRoute: String? = null
                        var lastNavTimeMs = 0L
                        { screen: Screens, isSelected: Boolean ->
                            if (playerBottomSheetState.isExpanded) {
                                playerBottomSheetState.collapseSoft()
                            }

                            // Search is an overlay, not a NavHost destination -- the
                            // fallback below would navigate("search_input"), a route that
                            // doesn't exist in the graph. The tablet side bar still lists
                            // Search as an item, so this cannot live only on the phone
                            // bar's search circle.
                            if (screen == Screens.Search) {
                                enterSearch()
                            } else if (isSelected) {
                                navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                coroutineScope.launch {
                                    topAppBarScrollBehavior.state.resetHeightOffset()
                                }
                            } else {
                                val now = SystemClock.elapsedRealtime()
                                // The debounce below only suppresses a genuine duplicate
                                // fire for a tab we are still sitting on. Once the user
                                // has moved anywhere else (tapped a song, opened an
                                // artist), the remembered route is stale — leaving it set
                                // meant a tab tap within the debounce window was silently
                                // swallowed and you stayed on the previous screen, which
                                // is why Home sometimes did nothing.
                                // effectiveRoute (not the raw destination route): it also
                                // reflects the search overlay, which the raw route can't.
                                if (effectiveRoute != lastNavRoute) {
                                    lastNavRoute = null
                                }
                                // Guards against a double-fire from the floating tab bar's
                                // predictive (press/drag-threshold) nav landing on top of the
                                // puck's own release-fire for the same target.
                                if (screen.route != lastNavRoute || now - lastNavTimeMs >= NavDebounceMs) {
                                    lastNavRoute = screen.route
                                    lastNavTimeMs = now
                                    // Plain navigate() with the standard multi-back-stack
                                    // pattern, same as every other route.
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }

                    val onSearchLongClick: () -> Unit = remember(navController) {
                        {
                            navController.navigate("recognition") {
                                launchSingleTop = true
                            }
                        }
                    }

                    // Pre-calculate values for graphicsLayer to avoid reading state during composition
                    val navBarTotalHeight = bottomInset + NavigationBarHeight

                    // Source for the top bar's TopFadeBlur on the top-level tabs (Home/
                    // Library/Search etc.) — tagged onto the NavHost's own content Box
                    // below via hazeSource. Detail screens (Album/Artist/Playlist) pass
                    // their own existing per-screen HazeState instead, already paid for
                    // by HeroMergeBand/HeroTopBlur.
                    val topBarHazeState = remember { HazeState() }

                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                AnimatedVisibility(
                                    visible = shouldShowTopBar,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsPadding(
                                                cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                            )
                                            // The side bar now spans most of the screen height,
                                            // not the small centered capsule this used to be — it
                                            // reaches the top bar's row, so the bar needs the same
                                            // start inset every screen's own content already gets.
                                            .padding(start = if (showRail) sideBarContentInset else 0.dp),
                                    ) {
                                        // Persistent, not scroll-linked — the blur (and the bar
                                        // above it) used to fade out with topBarChrome's
                                        // hide-on-scroll, which read as the whole bar vanishing
                                        // rather than staying put. shouldShowTopBar (per-route)
                                        // is still what actually hides this on Settings etc.
                                        TopFadeBlur(
                                            hazeState = topBarHazeState,
                                            // Honours the picked theme colour when dynamic
                                            // colour is off — same expression the old scrim used.
                                            pageColor = rememberAppBackgroundTint(
                                                AppTheme.colorScheme.background
                                            ),
                                        )
                                        val isHome = effectiveRoute == Screens.Home.route
                                        // Titles/wordmark stay neutral per theme; only hero-tinted
                                        // screens keep the artwork-derived accent text.
                                        val topBarAmbient = LocalAmbientBackgroundColor.current.value
                                        val topBarTitleColor = if (topBarAmbient.isSpecified && !topBarAmbient.isHueless()) {
                                            LocalAccentTextColor.current
                                        } else {
                                            AppTheme.colorScheme.onSurface
                                        }
                                        FrostedTopBar(
                                            leading = {
                                                if (isHome) {
                                                    // Home's own big logo (first item of its list)
                                                    // fades OUT with the same progress this fades
                                                    // IN with — see LocalHomeTopBarLogoProgress.
                                                    val logoProgress = LocalHomeTopBarLogoProgress.current
                                                    Text(
                                                        text = BrandName,
                                                        style = AppTheme.typography.titleLarge.copy(
                                                            fontFamily = rememberBrandFontFamily(),
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 26.sp,
                                                            letterSpacing = 1.5.sp,
                                                        ),
                                                        color = topBarTitleColor,
                                                        modifier = Modifier.graphicsLayer { alpha = logoProgress.floatValue },
                                                    )
                                                } else {
                                                    Text(
                                                        text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                        style = AppTheme.typography.titleLarge.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 24.sp,
                                                        ),
                                                        color = topBarTitleColor,
                                                    )
                                                }
                                            },
                                            actions = {
                                                // History/Stats/Together moved to Settings (see
                                                // SettingsScreen.kt's ACTIVITY section + the
                                                // existing ACCOUNT entry) — the top bar now
                                                // shows only the wordmark and this settings/
                                                // profile pill, per the simplified-chrome pass.
                                                // GlassCircleButton (not a plain IconButton) so
                                                // this gets the same liquid-glass rim + elastic
                                                // press stretch as every other floating chrome
                                                // button (back/share on detail screens).
                                                GlassCircleButton(applyAdditiveChrome = false, forceFlat = true, onClick = {
                                                      if (enableSettingsPopup) {
                                                          showSettingDialoge = true
                                                      } else {
                                                          navController.navigate(Screens.Settings.route) {
                                                              popUpTo(navController.graph.startDestinationId) {
                                                                  saveState = true
                                                              }
                                                              launchSingleTop = true
                                                              restoreState = true
                                                          }
                                                      }
                                                  }) {
                                                    BadgedBox(badge = {}) {
                                                        if (accountImageUrl != null) {
                                                            AsyncImage(
                                                                model = accountImageUrl,
                                                                contentDescription = stringResource(com.convx.music.R.string.account),
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .clip(CircleShape)
                                                            )
                                                        } else {
                                                            val composition by rememberLottieComposition(
                                                                LottieCompositionSpec.RawRes(com.convx.music.R.raw.setting)
                                                            )
                                                            val progress by animateLottieCompositionAsState(
                                                                composition = composition,
                                                                isPlaying = true,
                                                                iterations = 1,
                                                                speed = 1.5f
                                                            )

                                                            LottieAnimation(
                                                                composition = composition,
                                                                progress = { progress },
                                                                modifier = Modifier.size(34.dp),
                                                                contentScale = ContentScale.Fit
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        bottomBar = {
                            // The player and navigation elements moved to the root Box to escape
                            // the Scaffold's shrink modifier, preventing the "double shrink"
                            // where the player would shrink back even when it was the one
                            // expanding.
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                            .iosSheetPresentation(scaffoldPresentation, statusBarTop)
                    ) {
                        // No paddingValues consumed here on purpose: the top bar
                        // floats over content (TopFadeBlur/FrostedTopBar are an
                        // overlay, not a space-reserving bar), and every screen
                        // already carries its own top clearance via
                        // LocalPlayerAwareWindowInsets. Applying Scaffold's
                        // paddingValues here on top of that pushed every screen's
                        // content down by the bar's height a second time.
                        Box(Modifier.fillMaxSize()) {
                            val onRailItemClick: (Screens, Boolean) -> Unit = remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState, enterSearch) {
                                var lastNavRoute: String? = null
                                var lastNavTimeMs = 0L
                                { screen: Screens, isSelected: Boolean ->
                                    if (playerBottomSheetState.isExpanded) {
                                        playerBottomSheetState.collapseSoft()
                                    }

                                    // Search is an overlay, not a NavHost destination -- the
                                    // fallback below would navigate("search_input"), a route that
                                    // doesn't exist in the graph. The tablet side bar still lists
                                    // Search as an item, so this cannot live only on the phone
                                    // bar's search circle.
                                    if (screen == Screens.Search) {
                                        enterSearch()
                                    } else if (isSelected) {
                                        navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                        coroutineScope.launch {
                                            topAppBarScrollBehavior.state.resetHeightOffset()
                                        }
                                    } else {
                                        val now = SystemClock.elapsedRealtime()
                                        if (screen.route != lastNavRoute || now - lastNavTimeMs >= NavDebounceMs) {
                                            lastNavRoute = screen.route
                                            lastNavTimeMs = now
                                            // Plain navigate() with the standard multi-back-stack
                                            // pattern, same as every other route.
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }
                            }

                            val onRailSearchLongClick: () -> Unit = remember(navController) {
                                {
                                    navController.navigate("recognition") {
                                        launchSingleTop = true
                                    }
                                }
                            }

                            Box(Modifier.fillMaxSize()) {
                                // Everything the NavHost renders sits inside one shared
                                // transition scope, so a tile's artwork on one destination
                                // and the hero on the next are two ends of the same element
                                // rather than two images that cross-fade. Destinations opt
                                // in individually -- see sharedComposable in
                                // NavigationBuilder.kt and Modifier.sharedArtwork.
                                SharedTransitionLayout {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                ) {
                                // Carries layerBackdrop now -- see the comment on the Search
                                // AnimatedVisibility below for why this moved off NavHost's
                                // own modifier and up to wrap both NavHost and Search.
                                Box(
                                    modifier = Modifier
                                        // Predictive back scrubs popEnterTransition/popExitTransition
                                        // (both plain fades, see below) by live gesture progress. Mid
                                        // gesture that means BOTH the outgoing and incoming screen sit
                                        // at partial alpha at once, and with no opaque fill under
                                        // either one the window's default black showed straight
                                        // through the gap -- a partially-released back swipe flashed
                                        // black instead of just settling back to the current screen.
                                        .background(AppTheme.colorScheme.background)
                                        // What the top-level tabs' TopFadeBlur samples — see
                                        // topBarHazeState above. Skipped on settings routes:
                                        // they're never a shouldShowTopBar destination (this
                                        // source has no consumer there) and each settings page
                                        // now runs its own hazeSource via GlassScreenChrome —
                                        // nesting that inside this outer one made the inner
                                        // source draw nothing (Haze's zIndex filter excludes
                                        // it), which silently killed the per-screen progressive
                                        // blur on every settings page.
                                        .then(
                                            if (currentRoute == "settings" || currentRoute?.startsWith("settings/") == true) {
                                                Modifier
                                            } else {
                                                Modifier.hazeSource(topBarHazeState)
                                            },
                                        )
                                        // Skipped on WebView routes: layerBackdrop records
                                        // this whole subtree into a GraphicsLayer so glass
                                        // can sample it, which means the subtree is drawn
                                        // twice per frame. A WebView renders on its own
                                        // hardware canvas and does not survive that second
                                        // pass intact â€” the page tears and flickers while
                                        // still being usable, exactly the reported symptom.
                                        // Those screens are plain full-bleed WebViews with
                                        // their own Material top bar, so they have no glass
                                        // to feed anyway.
                                        // Also skipped when nothing would sample the backdrop:
                                        // every glass component switched off, or a device where
                                        // glass can't render at all (pre-Android 12, or low-RAM).
                                        // Recording the screen into a GraphicsLayer costs the
                                        // same whether or not anyone reads it, and on those
                                        // devices nobody ever does â€” measured at ~25ms of
                                        // display-list recording per frame on a Galaxy M34, on
                                        // every screen including a plain settings list.
                                        .then(
                                            if (DIAG_DISABLE_BACKDROP ||
                                                currentRoute in WebViewRoutes ||
                                                !glassEffectConfig.anyComponentEnabled ||
                                                !backdropHasConsumer ||
                                                !glassAllowed
                                            ) {
                                                Modifier
                                            } else {
                                                // Attach exactly one backdrop source, matching
                                                // Melox's scaffold. The previous outer+inner
                                                // full-screen graphicsLayer pair forced the
                                                // changing NavHost into one giant RenderNode;
                                                // every scroll then rebuilt that entire node
                                                // even while layerBackdrop itself was frozen.
                                                // The backdrop node already throttles idle
                                                // recording and freezes during scroll/nav, so
                                                // extra isolation layers only defeated Compose's
                                                // incremental child-RenderNode reuse.
                                                Modifier.layerBackdrop(
                                                    appBackdrop,
                                                    frozen = backdropFrozenProvider,
                                                )
                                            }
                                        )
                                        .nestedScroll(backdropFreezeConnection)
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                        .nestedScroll(floatingNavBarScrollConnection)
                                ) {
                                // NavHost with animations (Material 3 Expressive style)
                                NavHost(
                                    navController = navController,
                                    // Which tab this opens on (launch shortcut / the
                                    // default-open-tab preference) -- see initialMainTabRoute.
                                    startDestination = initialMainTabRoute,
                                    // iOS parallax push. The incoming screen travels the FULL
                                    // width; the outgoing one slides a third of that and dims,
                                    // and the difference between the two speeds is what reads
                                    // as depth. Both share Motion.push()'s curve so they stay
                                    // locked together.
                                    //
                                    // This replaced an index-based direction scheme that had
                                    // been dead since the pager migration and was actively
                                    // wrong: it looked routes up in `navigationItems`, but
                                    // every tab now lives inside the `main_tabs` pager, so the
                                    // outgoing route was ALWAYS `main_tabs` and both lookups
                                    // returned -1. `exitTransition`'s `-1 > -1` is false, so
                                    // the else branch ran and the outgoing screen slid RIGHT
                                    // while the incoming screen also slid in from the right —
                                    // both travelling the same way. That is what made every
                                    // non-morph push (settings sub-pages most visibly) look
                                    // broken. Pop happened to be correct by accident.
                                    enterTransition = {
                                        slideInHorizontally(Motion.push()) { it }
                                    },
                                    exitTransition = {
                                        // A destination that grows out of one of this
                                        // screen's tiles must not slide it away underneath:
                                        // the tile is the thing the next screen is coming
                                        // out of, so this one holds still and dims.
                                        if (isMorphRoute(targetState.destination.route)) {
                                            fadeOut(tween(200), targetAlpha = 0.7f)
                                        } else {
                                            slideOutHorizontally(Motion.push()) {
                                                Motion.parallaxOffset(it)
                                            } + fadeOut(Motion.push(), targetAlpha = Motion.PushDimAlpha)
                                        }
                                    },
                                    // Pop is a plain crossfade -- both directions, every
                                    // route, morph or not. Predictive back scrubs whatever
                                    // spec is here by the GESTURE's live drag progress rather
                                    // than running it on a timer, and only a plain alpha tween
                                    // scrubs cleanly: a fraction of a fade is still a smaller
                                    // fade, but a fraction of a slide-with-parallax-offset (or
                                    // the morph's spring-driven shrink-to-tile) has no clean
                                    // meaning at partial progress, which is what was showing up
                                    // as the outgoing and incoming screens seemingly both
                                    // sliding into place at once during a back-gesture drag.
                                    // Push keeps its slide/morph above -- only pop needed to
                                    // change, since only pop is ever gesture-driven.
                                    //
                                    // popEnterTransition is None, not a fade: fading BOTH
                                    // screens (incoming 0->1 AND outgoing 1->0) meant mid-drag
                                    // they sat at ~50% alpha each simultaneously -- a blend of
                                    // two half-transparent screens over whatever's behind them,
                                    // which read as a black flash (worse with pureBlack, where
                                    // that backing is literally Color.Black) independent of
                                    // whether the backing itself is opaque. The incoming screen
                                    // was already fully revealed underneath the whole time, so
                                    // it never needed to animate at all -- staying opaque means
                                    // there is nothing to blend through, in any theme.
                                    popEnterTransition = {
                                        EnterTransition.None
                                    },
                                    popExitTransition = {
                                        fadeOut(tween(220))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                                }
                                } // CompositionLocalProvider(LocalSharedTransitionScope)
                                } // SharedTransitionLayout
                            }

                            com.convx.music.ui.component.RingtoneTrimmerDialog(
                                isVisible = ringtoneUiState.showTrimmer,
                                songId = ringtoneUiState.targetSongId,
                                songTitle = ringtoneUiState.targetSongTitle,
                                duration = ringtoneUiState.targetSongDuration,
                                onDismiss = { ringtoneViewModel.hideTrimmer() },
                                onResolveStreamUrl = { ringtoneViewModel.getStreamUrl(this@MainActivity, it) },
                                onConfirm = { start, end -> ringtoneViewModel.setAsRingtone(this@MainActivity, start, end) }
                            )

                            if (ringtoneUiState.showProgress) {
                                com.convx.music.ui.component.RingtoneProgressDialog(
                                    isVisible = ringtoneUiState.showProgress,
                                    progress = ringtoneUiState.progress,
                                    statusMessage = ringtoneUiState.statusMessage,
                                    isComplete = ringtoneUiState.isComplete,
                                    isSuccess = ringtoneUiState.isSuccess,
                                    onDismiss = { ringtoneViewModel.dismissProgress() },
                                    onOpenSettings = { ringtoneViewModel.openRingtoneSettings(this@MainActivity) }
                                )
                            }

                            com.convx.music.ui.component.DonationPromptHost()

                            // Both float OVER the full-width NavHost, exactly as the
                            // bottom bar does on a phone: nothing reserves layout
                            // width for them, so content runs underneath instead of
                            // being clipped short of them.
                            //
                            // Ambient mode forces landscape orientation (see AmbientModeScreen's
                            // DisposableEffect), which alone flips showRail true on a phone that
                            // would never otherwise qualify for the rail layout — excluded here
                            // the same way ambient_mode is already excluded from the bottom/
                            // floating nav bar above, or the rail floats over its full-screen
                            // visualizer with no route-based reason to.
                            if (showRail && currentRoute != "ambient_mode" && currentRoute != "settings/appearance/diy") {
                                // No global floating Back here anymore: each screen's own
                                // back button (TopAppBar nav icon or floating chrome row)
                                // now insets clear of the side panel, so a second global
                                // one just duplicated it.

                                // Everything the top bar carries on a phone moves in
                                // here, plus the user's pinned playlists â€” the DAO's
                                // playlists() already filters to bookmarkedAt, so this
                                // is the pinned set, not every local playlist.
                                val sidebarPlaylists by database
                                    .playlists(PlaylistSortType.NAME, descending = false)
                                    .collectAsStateWithLifecycle(initialValue = emptyList())
                                val sidebarSections = remember(
                                    showHistoryButton,
                                    showStatsButton,
                                    listenTogetherInTopBar,
                                    sidebarPlaylists,
                                ) {
                                    buildList {
                                        val quick = buildList {
                                            if (showHistoryButton) {
                                                add(
                                                    SideBarLink(
                                                        label = getString(R.string.history),
                                                        iconRes = R.drawable.music_history,
                                                        onClick = { navController.navigate("history") },
                                                    )
                                                )
                                            }
                                            if (showStatsButton) {
                                                add(
                                                    SideBarLink(
                                                        label = getString(R.string.stats),
                                                        iconRes = R.drawable.stats,
                                                        onClick = { navController.navigate("stats") },
                                                    )
                                                )
                                            }
                                            if (listenTogetherInTopBar) {
                                                add(
                                                    SideBarLink(
                                                        label = getString(R.string.together),
                                                        iconRes = R.drawable.group_outlined,
                                                        onClick = {
                                                            navController.navigate("listen_together_from_topbar")
                                                        },
                                                    )
                                                )
                                            }
                                        }
                                        if (quick.isNotEmpty()) add(SideBarSection(links = quick))

                                        if (sidebarPlaylists.isNotEmpty()) {
                                            add(
                                                SideBarSection(
                                                    title = getString(R.string.playlists),
                                                    links = sidebarPlaylists.map { playlist ->
                                                        SideBarLink(
                                                            label = playlist.playlist.name,
                                                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                                            onClick = {
                                                                navController.navigate(
                                                                    "local_playlist/${playlist.id}"
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            )
                                        }
                                    }
                                }

                                AppFloatingSideBar(
                                    navigationItems = navigationItems,
                                    currentRoute = effectiveRoute,
                                    onItemClick = onRailItemClick,
                                    sections = sidebarSections,
                                    pureBlack = pureBlack,
                                    collapsed = sideBarCollapsed,
                                    onToggleCollapsed = { onSideBarCollapsedChange(!sideBarCollapsed) },
                                    footer = { footerCollapsed ->
                                        SideBarAccountRow(
                                            accountImageUrl = accountImageUrl,
                                            collapsed = footerCollapsed,
                                            onClick = {
                                                if (enableSettingsPopup) {
                                                    showSettingDialoge = true
                                                } else {
                                                    navController.navigate(Screens.Settings.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                        )
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .windowInsetsPadding(
                                            windowsInsets.only(WindowInsetsSides.Vertical)
                                        )
                                        .padding(start = SideBarMargin, top = 8.dp, bottom = 8.dp),
                                )

                                // The phone bar's docked accessory, floating free on
                                // the opposite edge â€” same pill, same glass, bottom
                                // right rather than centred. Swaps for the real
                                // search input bar while searching, same slide+fade
                                // the phone's own AppFloatingNavBar uses to hide its
                                // mini player for the keyboard.
                                if (playerMediaMetadata != null || inSearchScreen) {
                                    // Centred on the content area, not the screen:
                                    // the side bar's space is padded out first, and
                                    // the pill takes 80% of whatever is left.
                                    BoxWithConstraints(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(start = sideBarContentInset),
                                    ) {
                                        val pillWidth = maxWidth * FloatingMiniPlayerWidthFraction
                                        AnimatedContent(
                                            targetState = inSearchScreen,
                                            transitionSpec = {
                                                if (targetState) {
                                                    (slideInVertically(
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                    ) { it / 2 } + fadeIn()) togetherWith
                                                        (slideOutVertically(
                                                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                                        ) { it } + fadeOut())
                                                } else {
                                                    (slideInVertically(
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                                    ) { it } + fadeIn()) togetherWith
                                                        (slideOutVertically(
                                                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                                        ) { it / 2 } + fadeOut())
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .windowInsetsPadding(
                                                    windowsInsets.only(WindowInsetsSides.Bottom)
                                                )
                                                .padding(bottom = 12.dp),
                                            label = "tabViewSearchPill",
                                        ) { searching ->
                                            if (searching) {
                                                NavBarSearchInputBar(
                                                    state = navSearchState,
                                                    pureBlack = pureBlack,
                                                    modifier = Modifier.width(pillWidth),
                                                )
                                            } else if (playerMediaMetadata != null) {
                                                AppFloatingNowPlayingPill(
                                                    onClick = { playerBottomSheetState.expandSoft() },
                                                    onLyricsClick = {
                                                        playerBottomSheetState.expandSoft()
                                                        playerConnection?.requestShowLyrics?.value = true
                                                    },
                                                    onQueueClick = {
                                                        playerBottomSheetState.expandSoft()
                                                        playerConnection?.requestShowQueue?.value = true
                                                    },
                                                    pureBlack = pureBlack,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Player and Navigation Layer
                    // This layer floats over the Scaffold and ONLY shrinks when a menu sheet
                    // is open, escaping the Scaffold's own shrink-when-player-expands.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .iosSheetPresentation(playerLayerPresentation, statusBarTop)
                    ) {
                        if (!showRail && !showSettingDialoge && currentRoute?.startsWith("settings/") != true && currentRoute !in setOf("wrapped", "update", "listen_together/chat", "login", "equalizer", "ambient_mode")) {
                            Box(Modifier.fillMaxSize()) {
                                // Apple Music-style progressive scrim
                                if (appleMusicUi && isGlassAllowed()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(navBarTotalHeight + 56.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    0f to Color.Transparent,
                                                    0.35f to baseBg.copy(alpha = 0.15f),
                                                    0.6f to baseBg.copy(alpha = 0.4f),
                                                    0.85f to baseBg.copy(alpha = 0.7f),
                                                    1f to baseBg,
                                                )
                                            )
                                    )
                                }

                                if (pooledGlass != null) {
                                    // Draws nothing: it only keeps the shared blur current,
                                    // and must run after the content records and before the
                                    // chrome samples it.
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .glassPool(
                                                source = appBackdrop,
                                                pooled = glassPoolBackdrop,
                                                effects = poolEffects,
                                                scale = GlassPoolScale,
                                            )
                                    )
                                }

                                Box(
                                    modifier = Modifier.zIndex(if (playerAboveBars) 1f else 0f),
                                ) {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack
                                    )
                                }

                                // The shared blur is safe here and only here: this chrome is a
                                // sibling of the recorded content, not inside it. A glass surface
                                // within the content that sampled a backdrop containing that same
                                // content closes a RenderNode cycle -- HWUI recurses through
                                // prepareTree until it crashes.
                                CompositionLocalProvider(LocalPooledGlass provides pooledGlass) {
                                    AppFloatingNavBar(
                                        navigationItems = floatingNavigationItems,
                                        currentRoute = effectiveRoute,
                                        onItemClick = onNavItemClick,
                                        scrollConnection = floatingNavBarScrollConnection,
                                        pureBlack = pureBlack,
                                        showPlayerAccessory = hasDockedPlayerAccessory,
                                        onAccessoryClick = { playerBottomSheetState.expandSoft() },
                                        onAccessoryLyricsClick = {
                                            playerBottomSheetState.expandSoft()
                                            playerConnection?.requestShowLyrics?.value = true
                                        },
                                        onAccessoryQueueClick = {
                                            playerBottomSheetState.expandSoft()
                                            playerConnection?.requestShowQueue?.value = true
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .widthIn(max = 500.dp)
                                            .padding(horizontal = AppleTokens.Gutter)
                                            .padding(bottom = bottomInset + 8.dp)
                                            .graphicsLayer {
                                                val hiddenOffset =
                                                    size.height + (bottomInset + 8.dp).toPx()
                                                val navBarHeightPx = navigationBarHeight.toPx()
                                                translationY = if (navBarHeightPx == 0f) {
                                                    hiddenOffset
                                                } else {
                                                    hiddenOffset * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                }

                                            }
                                    )
                                }
                            }
                        } else {
                            if (currentRoute != "wrapped" && currentRoute != "update" && currentRoute != "listen_together/chat" && currentRoute != "ambient_mode") {
                                BottomSheetPlayer(
                                    state = playerBottomSheetState,
                                    navController = navController,
                                    pureBlack = pureBlack
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .height(bottomInsetDp)
                                    .graphicsLayer {
                                        val progress = playerBottomSheetState.progress
                                        alpha = if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                    }
                                    .background(baseBg)
                            )
                        }
                    }
                } // Box end

                // Every long-press / "more" menu is the iOS 27 glass popup now (replaces the
                    // bottom sheet everywhere; the old overlayMenuStyle preference is no longer
                    // read). Glass only while the player is down: over the expanded player the
                    // app backdrop it samples is the page underneath, not the player.
                    OverlayMenu(
                        state = LocalMenuState.current,
                        glass = !playerBottomSheetState.isExpanded,
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // Hosted at the root, not on the Listen Together screen: a
                    // join request is useless if it only shows on the one screen
                    // the host is probably not looking at.
                    ListenTogetherOverlay(
                        manager = LocalListenTogetherManager.current,
                        playerConnection = playerConnection,
                    )

                    // Same reasoning as the overlay above: playQueue is a single choke point,
                    // so its keep-or-clear prompt is hosted once here instead of at ~50 call
                    // sites.
                    QueueConflictDialog(playerConnection = playerConnection)
                    QueueReplacedUndoSnackbar(
                        playerConnection = playerConnection,
                        snackbarHostState = snackbarHostState,
                    )



                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showSettingDialoge) {
                        SettingDialoge(
                            onDismissRequest = { showSettingDialoge = false },
                            onNavigate = { route ->
                                showSettingDialoge = false
                                if (route in TabRootRoutes) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    navController.navigate(route)
                                }
                            },
                            homeViewModel = homeViewModel
                        )
                    }
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope
        // Allowlist: YouTube-style ids only, no slashes/routes injection.
        fun cleanId(raw: String?, min: Int = 4, max: Int = 64): String? {
            if (raw.isNullOrBlank()) return null
            val v = raw.trim()
            if (v.length !in min..max) return null
            if (v.contains("/") || v.contains("?") || v.contains("#") || v.contains(" ")) return null
            if (!v.matches(Regex("^[A-Za-z0-9_\\-]+\$"))) return null
            return v
        }

        val listenCode = cleanId(
            uri.getQueryParameter("code")
            ?: uri.getQueryParameter("room")
            ?: uri.pathSegments.getOrNull(1), min = 4, max = 16
        )
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" || uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
            listenTogetherManager.joinRoom(listenCode, username)
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> cleanId(uri.getQueryParameter("list"), max = 128)?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            cleanId(songs.firstOrNull()?.album?.id)?.let { browseId ->
                                withContext(Dispatchers.Main) {
                                    navController.navigate("album/$browseId") { launchSingleTop = true }
                                }
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId") { launchSingleTop = true }
                }
            }

            "browse" -> cleanId(uri.lastPathSegment)?.let { browseId ->
                navController.navigate("album/$browseId") { launchSingleTop = true }
            }

            "channel", "c" -> cleanId(uri.lastPathSegment)?.let { artistId ->
                navController.navigate("artist/$artistId") { launchSingleTop = true }
            }

            "search" -> {
                uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.take(200)?.let {
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}") { launchSingleTop = true }
                }
            }

            else -> {
                val videoId = cleanId(
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }, min = 6, max = 32
                )

                val playlistId = cleanId(uri.getQueryParameter("list"), max = 128)

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(videoId), playlistId).onSuccess { queue ->
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                        queue.firstOrNull()?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(null, playlistId).onSuccess { queue ->
                            val firstItem = queue.firstOrNull()
                            withContext(Dispatchers.Main) {
                                playerConnection?.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                        firstItem?.toMediaMetadata()
                                    )
                                )
                            }
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel = staticCompositionLocalOf<com.convx.music.ui.screens.settings.RingtoneViewModel> { error("No RingtoneViewModel provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<com.convx.music.listentogether.ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }

/**
 * The current screen's own background/hero colour (Album/Artist/Playlist's extracted
 * artwork tint, Home's background image tint, etc.), published by whichever screen is
 * on top so the nav bar and mini player — siblings of the NavHost, not descendants of
 * any one screen — can boost their glass rim light from it. A screen with no hero of
 * its own should set [Color.Unspecified] so readers fall back to their own default
 * rather than holding a stale colour from whatever screen set it last.
 *
 * A single process-wide [MutableState], not one scoped per composition: the nav bar
 * and every screen need to agree on the *same* instance despite having no shared
 * ancestor to `CompositionLocalProvider` it from (the nav bar is a sibling of the
 * NavHost, not a descendant of any one screen), and there is only ever one "current
 * screen" regardless of which Activity or window reads it.
 */
private val ambientBackgroundColorState = mutableStateOf(Color.Unspecified)
val LocalAmbientBackgroundColor = staticCompositionLocalOf { ambientBackgroundColorState }

/** Publishes [color] as the current screen's ambient background for the nav bar's
 *  rim tint. Call once near the top of a screen; resets to [Color.Unspecified] when
 *  the screen leaves composition so the next screen's own default isn't inherited. */
@Composable
fun PublishAmbientBackgroundColor(color: Color) {
    val ambient = LocalAmbientBackgroundColor.current
    // Tracks every value of color, not just its initial one -- a plain LaunchedEffect
    // keyed on color would re-fire per change, which is what's wanted here, but
    // clearing on screen-exit needs its own effect keyed on Unit so it fires once,
    // on leaving composition, not on every colour update in between.
    LaunchedEffect(color) { ambient.value = color }
    DisposableEffect(Unit) {
        onDispose { ambient.value = Color.Unspecified }
    }
}

/**
 * True while the app is laid out for tab view â€” the vertical floating side bar
 * instead of the bottom bar. Screens read this to switch their header to the
 * wide arrangement (hero as a card with its title beside it) rather than the
 * phone's full-bleed hero.
 */
val LocalTabView = compositionLocalOf { false }



