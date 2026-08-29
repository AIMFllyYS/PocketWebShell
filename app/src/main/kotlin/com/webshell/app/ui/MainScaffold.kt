package com.webshell.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webshell.app.shell.ShellScreen
import com.webshell.app.ui.MainScaffoldViewModel
import com.webshell.feature.add.AddScreen
import com.webshell.feature.browser.BrowserScreen
import com.webshell.feature.home.HomeScreen
import com.webshell.feature.me.MeScreen

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
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
