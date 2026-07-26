package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

@Composable
fun ReleaseOverlayScreen(
    canDrawOverlays: Boolean,
    screenCaptureGranted: Boolean,
    statusMessage: String,
    onRequestOverlayPermission: () -> Unit,
    onStartFloatingSubtitle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ikeyo_ojisan",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(text = "Overlay: ${if (canDrawOverlays) "allowed" else "needed"}")
            Text(text = "Screen capture: ${if (screenCaptureGranted) "allowed" else "needed"}")
            Text(text = statusMessage)
            Button(
                onClick = onRequestOverlayPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Allow overlay")
            }
            Button(
                onClick = onStartFloatingSubtitle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Start floating subtitle")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReleaseOverlayScreenPreview() {
    MyApplicationTheme {
        ReleaseOverlayScreen(
            canDrawOverlays = true,
            screenCaptureGranted = false,
            statusMessage = "Ready.",
            onRequestOverlayPermission = {},
            onStartFloatingSubtitle = {}
        )
    }
}
