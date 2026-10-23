package dev.raul.tvhome

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TvInput(val label: String, val isTuner: Boolean, val intent: Intent)

private fun loadInputs(context: Context): List<TvInput> {
    val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return runCatching { tim.tvInputList }.getOrDefault(emptyList())
        .filter { it.type == TvInputInfo.TYPE_HDMI || it.type == TvInputInfo.TYPE_TUNER }
        .filter { it.parentId == null } // skip per-device children of HDMI-CEC inputs
        .sortedWith(compareBy({ it.type != TvInputInfo.TYPE_HDMI }, { it.loadLabel(context).toString() }))
        .map { info ->
            val intent = if (info.type == TvInputInfo.TYPE_HDMI) {
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
            } else {
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(info.id))
            }
            TvInput(info.loadLabel(context).toString(), info.type == TvInputInfo.TYPE_TUNER, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SettingsPanel(open: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(visible = open, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            PanelContent(onClose)
        }
    }
}

@Composable
private fun PanelContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val inputs = remember { loadInputs(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .padding(48.dp)
            .width(680.dp)
            .fillMaxHeight()
            .panelGlass(RoundedCornerShape(52.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Settings", Modifier.weight(1f), style = TextStyle(color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.8).sp))
            GlassButton(shape = CircleShape, onClick = onClose, modifier = Modifier.size(64.dp)) { focused ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CloseIcon(if (focused) Color.Black else Color.White, 26.dp)
                }
            }
        }

        if (inputs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                inputs.take(3).forEachIndexed { i, input ->
                    GlassButton(
                        shape = RoundedCornerShape(28.dp),
                        onClick = { open(context, input.intent) },
                        modifier = Modifier.weight(1f).height(148.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) { focused ->
                        val ink = if (focused) Color.Black else Color.White
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            if (input.isTuner) TvIcon(ink, 34.dp) else HdmiIcon(ink, 34.dp)
                            BasicText(input.label, maxLines = 1, style = TextStyle(color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f)).padding(8.dp)) {
            val rows = listOf(
                "Picture" to Intent(Settings.ACTION_DISPLAY_SETTINGS),
                "Sound" to Intent(Settings.ACTION_SOUND_SETTINGS),
                "Wi-Fi" to Intent(Settings.ACTION_WIFI_SETTINGS),
            )
            rows.forEachIndexed { i, (label, intent) ->
                GlassButton(
                    shape = RoundedCornerShape(20.dp),
                    onClick = { open(context, intent) },
                    idleFill = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().height(76.dp).then(if (i == 0 && inputs.isEmpty()) Modifier.focusRequester(first) else Modifier),
                ) { focused ->
                    val ink = if (focused) Color.Black else Color.White
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(label, Modifier.weight(1f), style = TextStyle(color = ink, fontSize = 25.sp, fontWeight = FontWeight.Medium))
                        ChevronIcon(if (focused) Color.Black.copy(alpha = 0.5f) else TextSecondary, 18.dp)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        GlassButton(
            shape = RoundedCornerShape(28.dp),
            onClick = { open(context, Intent(Settings.ACTION_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
        ) { focused ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText("Open all TV settings", style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
