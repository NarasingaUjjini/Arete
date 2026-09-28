package com.trailmap.gps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.trailmap.gps.ui.theme.AlpineShape
import com.trailmap.gps.ui.theme.LocalAccent
import com.trailmap.gps.ui.theme.Canvas
import com.trailmap.gps.ui.theme.Hairline
import com.trailmap.gps.ui.theme.InstrumentPod
import com.trailmap.gps.ui.theme.JetBrainsMono
import com.trailmap.gps.ui.theme.OnSurface
import com.trailmap.gps.ui.theme.OnSurfaceVariant

@Composable
fun SaveRouteNameDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = InstrumentPod,
            shape = AlpineShape,
            border = BorderStroke(1.5.dp, LocalAccent.current)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "SAVE ROUTE",
                    color = OnSurface,
                    fontFamily = JetBrainsMono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "Saved locally. Offline immediately.",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 48) name = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    shape = AlpineShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalAccent.current,
                        unfocusedBorderColor = Hairline,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        cursorColor = LocalAccent.current
                    )
                )
                Text(
                    "${name.length} / 48",
                    color = OnSurfaceVariant,
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlpineOutlineButton("CANCEL", onDismiss, modifier = Modifier.weight(1f))
                    AlpinePrimaryButton("CONFIRM & SAVE", { onSave(name) }, modifier = Modifier.weight(1f), enabled = name.isNotBlank())
                }
            }
        }
    }
}
