package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.Amber
import com.trailmap.gps.ui.theme.Canvas
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.InstrumentBay
import com.trailmap.gps.ui.theme.InstrumentPod
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant
import com.trailmap.gps.ui.theme.OutlineVariant
import com.trailmap.gps.ui.theme.PodStroke
import com.trailmap.gps.ui.theme.SurfaceContainerHigh
import com.trailmap.gps.ui.theme.SurfaceContainerHighest
import com.trailmap.gps.ui.theme.SurfaceContainerLow
import com.trailmap.gps.ui.theme.TelemetryMuted
import com.trailmap.gps.ui.theme.TouchTarget

@Composable
fun AlpineSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = OnSurfaceVariant,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun AlpineSegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Canvas,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
                .height(TouchTarget),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(TouchTarget - 4.dp)
                        .clickable { onSelect(index) },
                    color = if (isSelected) Amber else Color.Transparent,
                    shape = AlpineShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option.uppercase(),
                            color = if (isSelected) Canvas else OnSurfaceVariant,
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlpineInlineSegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AlpineSegmentedControl(options, selected, onSelect, modifier)
}

@Composable
fun AlpineSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = InstrumentPod,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun AlpineSettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
fun AlpineRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) SurfaceContainerHigh else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                color = if (selected) OnSurface else OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = AlpineShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, if (selected) Amber else SurfaceContainerHighest)
                ) {}
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Amber)
                    )
                }
            }
        }
    }
}

@Composable
fun AlpineCardDivider() {
    HorizontalDivider(color = SurfaceContainerHighest, thickness = 1.dp)
}

@Composable
fun AlpineDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 12.dp)
            .size(width = 40.dp, height = 4.dp)
            .background(SurfaceContainerHighest)
    )
}

@Composable
fun AlpineBackRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("←", color = OnSurfaceVariant, fontFamily = JetBrainsMono, fontSize = 18.sp)
        Text(
            text = label.uppercase(),
            color = OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
fun AlpineSubHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = InstrumentBay,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(TouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(TouchTarget),
                color = Color.Transparent,
                shape = AlpineShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", color = OnSurface, fontFamily = JetBrainsMono, fontSize = 20.sp)
                }
            }
            Text(
                text = title.uppercase(),
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AlpineMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceContainerLow,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AlpineMetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = InstrumentBay,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = OnSurface,
                    fontFamily = JetBrainsMono,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit.uppercase(),
                        color = TelemetryMuted,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlpineStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = SurfaceContainerHigh,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = OnSurface, fontFamily = JetBrainsMono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                label.uppercase(),
                color = OnSurfaceVariant,
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AlpinePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(TouchTarget),
        enabled = enabled,
        color = if (enabled) Amber else Amber.copy(alpha = 0.4f),
        shape = AlpineShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.uppercase(),
                color = Canvas,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AlpineOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(TouchTarget),
        color = InstrumentPod,
        shape = AlpineShape,
        border = BorderStroke(1.dp, PodStroke)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.uppercase(),
                color = OnSurface,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AlpineIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = OutlineVariant,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(TouchTarget),
        color = SurfaceContainerHigh,
        shape = AlpineShape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlpineDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AlpineSectionLabel(label)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options.getOrElse(safeIndex) { "—" },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .height(TouchTarget),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = AlpineShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = Hairline,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    focusedTrailingIconColor = Amber,
                    unfocusedTrailingIconColor = OnSurfaceVariant,
                    cursorColor = Amber
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = InstrumentPod
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                color = if (index == safeIndex) Amber else OnSurface,
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlpineSourceBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = InstrumentBay,
        shape = AlpineShape,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = OnSurfaceVariant,
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}
