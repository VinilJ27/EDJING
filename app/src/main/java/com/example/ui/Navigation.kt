package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.EffectsSamplerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.RecordingStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TutorialsScreen
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.viewmodel.DjViewModel
import kotlinx.serialization.Serializable

@Serializable object ConsoleRoute
@Serializable object LibraryRoute
@Serializable object EffectsSamplerRoute
@Serializable object RecordingStudioRoute
@Serializable object TutorialsRoute
@Serializable object SettingsRoute

private data class NavTab(
    val route: Any,
    val label: String,
    val tag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_TABS = listOf(
    NavTab(ConsoleRoute, "Console", "nav_console", Icons.Filled.Album, Icons.Outlined.Album),
    NavTab(LibraryRoute, "Library", "nav_library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavTab(EffectsSamplerRoute, "FX & Pads", "nav_fx_sampler", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    NavTab(RecordingStudioRoute, "Studio", "nav_studio", Icons.Filled.FiberManualRecord, Icons.Outlined.FiberManualRecord),
    NavTab(TutorialsRoute, "School", "nav_tutorials", Icons.Filled.School, Icons.Outlined.School),
    NavTab(SettingsRoute, "Settings", "nav_settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun PulseMixApp(
    viewModel: DjViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    var preferredLibraryDeck by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        val useNavRail = maxWidth >= 500.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = VoidBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = ConsoleCharcoal,
                        tonalElevation = 0.dp
                    ) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeckACyan,
                                    selectedTextColor = DeckACyan,
                                    indicatorColor = DeckSurface,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    SlimLandscapeNavRail(
                        tabs = NAV_TABS,
                        currentDestination = currentDestination,
                        onSelect = { navigateTo(it) }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ConsoleRoute
                    ) {
                        composable<ConsoleRoute> {
                            ConsoleScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenLibraryForDeck = { deckIdx ->
                                    preferredLibraryDeck = deckIdx
                                    navigateTo(LibraryRoute)
                                }
                            )
                        }
                        composable<LibraryRoute> {
                            LibraryScreen(
                                state = state,
                                viewModel = viewModel,
                                preferredDeckIndex = preferredLibraryDeck,
                                onTrackLoadedToDeck = {
                                    preferredLibraryDeck = null
                                    navigateTo(ConsoleRoute)
                                }
                            )
                        }
                        composable<EffectsSamplerRoute> {
                            EffectsSamplerScreen(state = state, viewModel = viewModel)
                        }
                        composable<RecordingStudioRoute> {
                            RecordingStudioScreen(state = state, viewModel = viewModel)
                        }
                        composable<TutorialsRoute> {
                            TutorialsScreen(
                                state = state,
                                viewModel = viewModel,
                                onPracticeInConsole = { targetMode ->
                                    viewModel.setDeckPanelMode(0, targetMode)
                                    navigateTo(ConsoleRoute)
                                }
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(state = state, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlimLandscapeNavRail(
    tabs: List<NavTab>,
    currentDestination: NavDestination?,
    onSelect: (Any) -> Unit
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        tabs.forEach { tab ->
            val selected = currentDestination?.hasRoute(tab.route::class) == true
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag(tab.tag)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) DeckSurface else ConsoleCharcoal)
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) DeckACyan.copy(alpha = 0.65f) else ConsoleCharcoal,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(tab.route) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                    tint = if (selected) DeckACyan else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) DeckACyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
