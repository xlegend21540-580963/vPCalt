package com.aistudio.vortexpc.spike

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpikeScreen()
                }
            }
        }
    }
}

@Composable
fun SpikeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetSdk = context.applicationInfo.targetSdkVersion
    var isRunning by remember { mutableStateOf(false) }
    var logText by remember {
        mutableStateOf(
            "VortexSpike ready (targetSdk=$targetSdk, pkg=${context.packageName})\nTap \"Run all\" to execute Experiment 1 (exec matrix) and Experiment 2 (mmap matrix)."
        )
    }
    var hasResults by remember {
        mutableStateOf(SpikeRunner.resultsFile(context).exists())
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "VortexSpike | targetSdk=$targetSdk | ${context.packageName}",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isRunning) return@Button
                    isRunning = true
                    logText = "Running Experiment 1 & Experiment 2..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SpikeRunner.runAll(context)
                        }
                        logText = result
                        hasResults = SpikeRunner.resultsFile(context).exists()
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.testTag("run_all_button")
            ) {
                Text(if (isRunning) "Running..." else "Run all")
            }

            OutlinedButton(
                onClick = {
                    val file = SpikeRunner.resultsFile(context)
                    if (!file.exists()) {
                        Toast.makeText(context, "Run all tests first", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VortexSpike results targetSdk=$targetSdk")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, logText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share results.txt"))
                },
                enabled = hasResults && !isRunning,
                modifier = Modifier.testTag("share_results_button")
            ) {
                Text("Share results")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0E0E10))
                .padding(8.dp)
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll)
                .testTag("spike_log_view")
        ) {
            SelectionContainer {
                Text(
                    text = logText,
                    color = Color(0xFFE2E2E6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    softWrap = false
                )
            }
        }
    }
}
