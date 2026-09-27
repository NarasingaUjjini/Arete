package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.data.AppSettings
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.InstrumentBay
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.TelemetryMuted

@Composable
fun MapMetricsBar(
    settings: AppSettings,
    inputs: DataBarInputs,
    modifier: Modifier = Modifier
) {
    val readings = DataBar.readings(settings, inputs)
    val valueSize = if (settings.largeNumbers) 20.sp else 16.sp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (settings.largeNumbers) 64.dp else 56.dp)
            .semantics {
                contentDescription = readings.joinToString { "${it.label} ${it.value} ${it.unit}" }
            },
        color = InstrumentBay,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            readings.forEach { reading ->
                MetricCell(
                    reading = reading,
                    valueSize = valueSize,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    reading: DataBarReading,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = reading.label.uppercase(),
            color = OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            lineHeight = 11.sp
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = reading.value,
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                lineHeight = 20.sp
            )
            if (reading.unit.isNotEmpty()) {
                Text(
                    text = reading.unit.uppercase(),
                    color = TelemetryMuted,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
