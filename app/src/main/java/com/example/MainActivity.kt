package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.PromptInspectorDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppTab(val title: String) {
    CHAT("Chat & Test"),
    MEMORIES("Memories"),
    ACCOUNTS("Accounts"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentTab by remember { mutableStateOf(AppTab.CHAT) }
                val inspectResult by viewModel.inspectInjectionResult.collectAsStateWithLifecycle()
                val memories by viewModel.memories.collectAsStateWithLifecycle()
                val notice by viewModel.notice.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(notice) {
                    notice?.let { n ->
                        snackbarHostState.showSnackbar(n.message)
                        viewModel.dismissNotice()
                    }
                }

                // Handle system back button to return to Chat if on other tabs
                if (currentTab != AppTab.CHAT) {
                    BackHandler {
                        currentTab = AppTab.CHAT
                    }
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (currentTab) {
                                        AppTab.CHAT -> "Gemini Memory Layer"
                                        AppTab.MEMORIES -> "Local Memory Bank"
                                        AppTab.ACCOUNTS -> "Account Profiles"
                                        AppTab.SETTINGS -> "AI & Memory Settings"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("main_navigation_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == AppTab.CHAT,
                                onClick = { currentTab = AppTab.CHAT },
                                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                                label = { Text("Chat") },
                                modifier = Modifier.testTag("nav_item_chat")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.MEMORIES,
                                onClick = { currentTab = AppTab.MEMORIES },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (memories.isNotEmpty()) {
                                                Badge {
                                                    Text("${memories.size}")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Memory, contentDescription = "Memories")
                                    }
                                },
                                label = { Text("Memories") },
                                modifier = Modifier.testTag("nav_item_memories")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.ACCOUNTS,
                                onClick = { currentTab = AppTab.ACCOUNTS },
                                icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Accounts") },
                                label = { Text("Accounts") },
                                modifier = Modifier.testTag("nav_item_accounts")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppTab.SETTINGS,
                                onClick = { currentTab = AppTab.SETTINGS },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_item_settings")
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppTab.CHAT -> ChatScreen(
                                viewModel = viewModel,
                                onNavigateToMemories = { currentTab = AppTab.MEMORIES },
                                onNavigateToAccounts = { currentTab = AppTab.ACCOUNTS }
                            )
                            AppTab.MEMORIES -> MemoriesScreen(
                                viewModel = viewModel
                            )
                            AppTab.ACCOUNTS -> AccountsScreen(
                                viewModel = viewModel
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel
                            )
                        }
                    }

                    // Prompt Inspector Bottom Sheet / Dialog
                    inspectResult?.let { injection ->
                        PromptInspectorDialog(
                            injection = injection,
                            onDismiss = { viewModel.clearPromptInspection() },
                            onSendNow = { promptToSend ->
                                viewModel.sendMessage(promptToSend)
                            }
                        )
                    }
                }
            }
        }
    }
}
