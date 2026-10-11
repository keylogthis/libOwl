package org.thebottle.libOwl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.thebottle.libOwl.data.AccentColor
import org.thebottle.libOwl.data.LoanReminders
import org.thebottle.libOwl.ui.appColorScheme
import org.thebottle.libOwl.ui.BackupViewModel
import org.thebottle.libOwl.ui.BookEditScreen
import org.thebottle.libOwl.ui.BookEditViewModel
import org.thebottle.libOwl.ui.BookListScreen
import org.thebottle.libOwl.ui.BookListViewModel
import org.thebottle.libOwl.ui.ConfirmScanScreen
import org.thebottle.libOwl.ui.ConfirmScanViewModel
import org.thebottle.libOwl.ui.MultiScanScreen
import org.thebottle.libOwl.ui.HelpScreen
import org.thebottle.libOwl.ui.LoansScreen
import org.thebottle.libOwl.ui.LoansViewModel
import org.thebottle.libOwl.ui.LocalAppSettings
import org.thebottle.libOwl.ui.SettingsScreen
import org.thebottle.libOwl.ui.StatsScreen
import org.thebottle.libOwl.ui.StatsViewModel

class MainActivity : ComponentActivity() {
    /** Set when the app is opened from a loan reminder; the UI jumps to the Loans list and clears it. */
    private var openLoans by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openLoans = intent?.getBooleanExtra(EXTRA_OPEN_LOANS, false) == true
        setContent {
            val settings by (application as BookApp).settings.settings.collectAsState(initial = null)
            MaterialTheme(colorScheme = appColorScheme(settings?.accent ?: AccentColor.GREEN, isSystemInDarkTheme())) {
                AppNav(openLoans = openLoans, onOpenLoansHandled = { openLoans = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOANS, false)) openLoans = true
    }

    companion object {
        const val EXTRA_OPEN_LOANS = "org.thebottle.libOwl.OPEN_LOANS"
    }
}

@Composable
private fun AppNav(openLoans: Boolean, onOpenLoansHandled: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as BookApp
    val repo = app.repository
    val covers = app.covers
    val lookup = app.lookup
    val scope = rememberCoroutineScope()

    // Wait for settings to load so screens never flash the wrong currency.
    val settings by app.settings.settings.collectAsState(initial = null)
    val current = settings ?: return

    LaunchedEffect(openLoans) {
        if (openLoans) {
            nav.navigate("loans") { launchSingleTop = true }
            onOpenLoansHandled()
        }
    }

    CompositionLocalProvider(LocalAppSettings provides current) {
        NavHost(nav, startDestination = "list") {
            composable("list") {
                val vm: BookListViewModel = viewModel(factory = BookListViewModel.factory(repo))
                val showNeeds by it.savedStateHandle.getStateFlow("showNeedsDetails", false).collectAsState()
                LaunchedEffect(showNeeds) {
                    if (showNeeds) {
                        vm.updateFilters { f -> f.copy(needsDetailsOnly = true) }
                        it.savedStateHandle["showNeedsDetails"] = false
                    }
                }
                BookListScreen(
                    vm, covers,
                    onAdd = { nav.navigate("edit/0") },
                    onOpen = { nav.navigate("edit/$it") },
                    onSettings = { nav.navigate("settings") },
                    onHelp = { nav.navigate("help") },
                    onStats = { nav.navigate("stats") },
                    onLoans = { nav.navigate("loans") },
                    onScan = { nav.navigate("scan") },
                    onMultiScan = { nav.navigate("multiscan") },
                    onSaveReportPrefs = { p -> scope.launch { app.settings.setReportPrefs(p) } },
                    onBorrowerHidden = { name, hidden -> scope.launch { app.settings.setBorrowerHidden(name, hidden) } }
                )
            }
            composable(
                "edit/{bookId}?isbn={isbn}&lookup={lookup}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("isbn") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lookup") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val id = entry.arguments?.getLong("bookId") ?: 0L
                val isbn = entry.arguments?.getString("isbn").orEmpty()
                val doLookup = entry.arguments?.getBoolean("lookup") ?: false
                val vm: BookEditViewModel = viewModel(
                    key = "edit$id",
                    factory = BookEditViewModel.factory(repo, covers, lookup, id, isbn, doLookup)
                )
                BookEditScreen(
                    vm, covers,
                    onDone = { nav.popBackStack() },
                    onBorrowerHidden = { name, hidden -> scope.launch { app.settings.setBorrowerHidden(name, hidden) } },
                    // After saving (or deleting), always land on the main list, even if we came from a scanner.
                    onSaved = { nav.popBackStack("list", false) }
                )
            }
            composable("scan") {
                val vm: ConfirmScanViewModel = viewModel(factory = ConfirmScanViewModel.factory(repo, covers, lookup))
                ConfirmScanScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack("list", false) },
                    onEditFirst = { isbn, doLookup -> nav.navigate("edit/0?isbn=$isbn&lookup=$doLookup") }
                )
            }
            composable("multiscan") {
                val vm: ConfirmScanViewModel = viewModel(factory = ConfirmScanViewModel.factory(repo, covers, lookup))
                MultiScanScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onReviewNeedsDetails = {
                        nav.previousBackStackEntry?.savedStateHandle?.set("showNeedsDetails", true)
                        nav.popBackStack()
                    }
                )
            }
            composable("help") { HelpScreen(onBack = { nav.popBackStack() }) }
            composable("loans") {
                val vm: LoansViewModel = viewModel(factory = LoansViewModel.factory(repo))
                LoansScreen(vm, covers, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("edit/$it") })
            }
            composable("stats") {
                val vm: StatsViewModel = viewModel(factory = StatsViewModel.factory(repo))
                StatsScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                val backupVm: BackupViewModel = viewModel(factory = BackupViewModel.factory(app.backup))
                SettingsScreen(
                    backupVm,
                    onBack = { nav.popBackStack() },
                    onCurrencyChange = { option -> scope.launch { app.settings.setCurrency(option) } },
                    onAccentChange = { option -> scope.launch { app.settings.setAccent(option) } },
                    onLoanPrefsChange = { p ->
                        scope.launch {
                            app.settings.setLoanPrefs(p)
                            LoanReminders.sync(app, p.remindersEnabled)
                        }
                    }
                )
            }
        }
    }
}
