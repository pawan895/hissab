package com.pawan.hisaab.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.R
import com.pawan.hisaab.ui.MainViewModel
import com.pawan.hisaab.ui.components.PrimaryButton
import com.pawan.hisaab.ui.components.SecondaryButton
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.settings.PermissionsChecklist
import com.pawan.hisaab.ui.theme.FinTheme

@Composable
fun OnboardingScreen(vm: MainViewModel, onImportSheet: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val c = FinTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Box(
            Modifier.padding(top = 24.dp).size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher foreground is drawn on a 108dp canvas with the mark in the middle third.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(108.dp))
        }
        Text("Hisaab", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Every bank SMS becomes an entry. Tag it in one tap, and your monthly budget fills itself in.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink2,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "No internet access, so nothing leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.ink3,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionLabel("Permissions")
        PermissionsChecklist()

        SectionLabel("History (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            SecondaryButton("SMS · last month", { vm.scanInbox(1) }, enabled = !busy)
            SecondaryButton("Last 3 months", { vm.scanInbox(3) }, enabled = !busy)
        }
        SecondaryButton("Import Google Sheet CSV", onImportSheet, Modifier.padding(top = 10.dp), enabled = !busy)

        PrimaryButton("Start", { vm.settings.setOnboardingDone(true) }, Modifier.padding(top = 36.dp, bottom = 16.dp))
    }
}
