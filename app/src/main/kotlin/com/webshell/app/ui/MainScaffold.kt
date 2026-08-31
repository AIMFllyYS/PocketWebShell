package com.webshell.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.webshell.app.shell.ShellScreen
import com.webshell.app.ui.MainScaffoldViewModel
import com.webshell.core.designsystem.components.glassSurface
import com.webshell.core.designsystem.theme.AppMotion
import com.webshell.feature.add.AddScreen
import com.webshell.feature.browser.BrowserScreen
import com.webshell.feature.home.HomeScreen
import com.webshell.feature.me.MeScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Filled.Home),
    ADD("添加", Icons.Filled.Add),
    BROWSE("浏览", Icons.Filled.Public),
    ME("我的", Icons.Filled.Person),
}

@Composable
fun MainScaffold(
    launchUrl: String? = null,
    viewModel: MainScaffoldViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var openedUrl by rememberSaveable { mutableStateOf(launchUrl) }

    // 主页打开应用 / 外部 url 进入：全屏壳（无底栏，返回回到入口 tab）
    openedUrl?.let { url ->
        BackHandler { openedUrl = null }
        ShellScreen(initialUrl = url, immersive = true)
        return
    }

    // 冷启动直接带 url（外部唤起）时也要完成保活登记
    LaunchedEffect(launchUrl) {
        if (launchUrl != null) viewModel.registerKeepAliveFor(launchUrl)
    }

    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GlassBottomBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
                hazeState = hazeState,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 内容作为模糊采样源；顶部保留安全区，底部为悬浮胶囊预留空间
                .hazeSource(state = hazeState)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 96.dp,
                ),
        ) {
            // 主 Tab 切换：统一淡入淡出过渡（不改变布局尺寸）。
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(AppMotion.NormalMs),
                label = "main-tab",
            ) { tab ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(
                        onLaunch = { appId, _ ->
                            viewModel.launchApp(appId) { url -> openedUrl = url }
                        },
                        onAddRequested = { selectedTab = MainTab.ADD },
                    )
                    MainTab.ADD -> AddScreen(
                        onCreated = { selectedTab = MainTab.HOME },
                    )
                    MainTab.BROWSE -> BrowserScreen()
                    MainTab.ME -> MeScreen(
                        onKeepAliveServiceChanged = viewModel::setKeepAliveServiceEnabled,
                    )
                }
            }
        }
    }
}

/**
 * iOS Liquid Glass 风格悬浮胶囊底栏（Haze 实时背景模糊 + 高光描边）。
 * 全屏仅此一处实时模糊 backdrop，见 docs/PERFORMANCE.md。
 */
@Composable
private fun GlassBottomBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: HazeState,
) {
    val capsuleShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glassSurface(hazeState, shape = capsuleShape),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(),
                    label = "tabTint",
                )
                // 选中态胶囊指示底：只改背景色，不改变任何布局尺寸
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                    },
                    animationSpec = spring(),
                    label = "tabPill",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pillColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) }
                        .padding(vertical = 8.dp),
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint)
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
