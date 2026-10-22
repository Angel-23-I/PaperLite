package com.angel.paperlite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.angel.paperlite.ui.DetailScreen
import com.angel.paperlite.ui.HomeScreen
import com.angel.paperlite.ui.PaperLiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperLiteTheme {
                val vm: AppViewModel = viewModel()
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            onOpenDoc = { nav.navigate("doc/$it") },
                            onOpenPdf = { uri ->
                                startActivity(
                                    Intent(this@MainActivity, PdfViewerActivity::class.java)
                                        .setData(uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            },
                        )
                    }
                    composable("doc/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        DetailScreen(
                            vm = vm,
                            docId = it.arguments!!.getLong("id"),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
