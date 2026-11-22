package org.dumbtv.launcher.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dumbtv.launcher.apps.LaunchableApp
import org.dumbtv.launcher.usage.TvUsage
import java.util.Date

private val UsageShape = RoundedCornerShape(20.dp)

@Composable
internal fun HomeUsage(usage: TvUsage.Snapshot, apps: List<LaunchableApp>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mostUsed = remember(apps, usage.appMillis, context.packageName) {
        apps.asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { app -> usage.appMillis[app.packageName]?.let { app to it } }
            .sortedByDescending { (_, millis) -> millis }
            .take(5)
            .toList()
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier.widthIn(max = 248.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UsageGlass("Most used today", Modifier.fillMaxWidth()) {
                when {
                    !usage.hasAccess -> UsageLabel("Enable Usage access in Settings → General", maxLines = 2)
                    mostUsed.isEmpty() -> UsageLabel("No app activity yet")
                    else -> mostUsed.forEach { (app, millis) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Image(
                                painter = remember(app.icon) { DrawablePainter(app.icon) },
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)),
                            )
                            UsageLabel(app.label, Modifier.weight(1f))
                            UsageValue(formatDuration(millis))
                        }
                    }
                }
            }
            if (usage.hasAccess) {
                UsageGlass("Recent TV wake-ups", Modifier.fillMaxWidth()) {
                    if (usage.screenOns.isEmpty()) UsageLabel("No wake-ups recorded")
                    val dateFormat = DateFormat.getMediumDateFormat(context)
                    val timeFormat = DateFormat.getTimeFormat(context)
                    usage.screenOns.forEach { timestamp ->
                        val date = Date(timestamp)
                        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UsageLabel(if (DateUtils.isToday(timestamp)) "Today" else dateFormat.format(date),
                                Modifier.weight(1f))
                            UsageValue(timeFormat.format(date))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageGlass(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.homeGlass(UsageShape).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, color = Glass.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(Glass.Line))
        content()
    }
}

@Composable
private fun UsageLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(text, modifier, color = Glass.Paper.copy(alpha = 0.88f), fontSize = 13.sp,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun UsageValue(text: String) {
    Text(text, color = Glass.Ice, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes == 0L -> "<1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
