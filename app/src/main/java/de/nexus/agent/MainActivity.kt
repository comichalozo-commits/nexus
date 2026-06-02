package de.nexus.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.nexus.agent.navigation.NexusNavHost
import de.nexus.agent.ui.theme.NexusAgentTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { false }
        enableEdgeToEdge()

        setContent {
            NexusAgentTheme {
                NexusAppWithPermissions()
            }
        }
    }

    @Composable
    private fun NexusAppWithPermissions() {
        var allPermissionsGranted by remember { mutableStateOf(false) }
        var showPermissionRationale by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (!allGranted) {
                val shouldShowRationale = getRequiredPermissions().any { perm ->
                    shouldShowRequestPermissionRationale(perm)
                }
                if (!shouldShowRationale) {
                    showPermissionRationale = true
                }
            }
            allPermissionsGranted = allGranted
        }

        LaunchedEffect(Unit) {
            val requiredPermissions = getRequiredPermissions()
            val notGranted = requiredPermissions.filter { perm ->
                ContextCompat.checkSelfPermission(this@MainActivity, perm) !=
                    PackageManager.PERMISSION_GRANTED
            }

            if (notGranted.isEmpty()) {
                allPermissionsGranted = true
            } else {
                permissionLauncher.launch(notGranted.toTypedArray())
            }
        }

        if (allPermissionsGranted) {
            NexusApp()
        } else {
            PermissionSplashScreen(
                onRequestPermission = {
                    val requiredPermissions = getRequiredPermissions()
                    val notGranted = requiredPermissions.filter { perm ->
                        ContextCompat.checkSelfPermission(this@MainActivity, perm) !=
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (notGranted.isNotEmpty()) {
                        permissionLauncher.launch(notGranted.toTypedArray())
                    }
                },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                },
                showRationale = showPermissionRationale
            )
        }
    }

    @Composable
    fun NexusApp() {
        val navController = rememberNavController()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NexusNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.VIBRATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions
    }
}

@Composable
fun PermissionSplashScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    showRationale: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Berechtigungen erforderlich",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Nexus Agent benötigt Zugriff auf Mikrofon, Kamera, Standort und weitere Berechtigungen, um alle Funktionen bereitzustellen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = showRationale,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Berechtigungen wurden verweigert. Bitte in den Einstellungen aktivieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenSettings) {
                        Text("Einstellungen öffnen")
                    }
                }
            }

            AnimatedVisibility(
                visible = !showRationale,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(onClick = onRequestPermission) {
                    Text("Berechtigungen erteilen")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NexusAppPreview() {
    NexusAgentTheme {
        val navController = rememberNavController()
        NexusNavHost(navController = navController)
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionSplashPreview() {
    NexusAgentTheme {
        PermissionSplashScreen(
            onRequestPermission = {},
            onOpenSettings = {},
            showRationale = false
        )
    }
}
