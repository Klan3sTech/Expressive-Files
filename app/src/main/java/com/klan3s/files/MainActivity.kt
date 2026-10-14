package com.klan3s.files

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.klan3s.files.ui.ExpressiveFilesApp
import com.klan3s.files.ui.MainViewModel
import com.klan3s.files.ui.theme.ExpressiveFilesTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ExpressiveFilesTheme(vm.settings) { ExpressiveFilesApp(vm) } }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { vm.openUri(it) }
    }
}
