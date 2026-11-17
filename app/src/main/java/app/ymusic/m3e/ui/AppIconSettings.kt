package app.ymusic.m3e.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.R

enum class AppIcon(val title: String, val alias: String, @DrawableRes val preview: Int) {
    ExMusic("ExMusic", "IconDefault", R.drawable.icon_preview_exmusic),
    NotClaude("(не) клод", "IconNotClaude", R.drawable.icon_preview_notclaude),
    Brown("говно", "IconBrown", R.drawable.icon_preview_brown),
    Indigo("Индиго", "IconIndigo", R.drawable.icon_preview_indigo),
    Swear("клянусь", "IconSwear", R.drawable.icon_preview_swear),
    Limits("лимиты - В С Е", "IconLimits", R.drawable.icon_preview_limits),
    Matcha("Матча", "IconMatcha", R.drawable.icon_preview_matcha),
    BlackWhite("Черно-белая", "IconBw", R.drawable.icon_preview_bw),
}

object AppIconSettings {
    private var inited = false

    var selected by mutableStateOf(AppIcon.ExMusic)
        private set

    private var applied by mutableStateOf<AppIcon?>(null)

    val pending: Boolean get() = selected != applied

    private fun component(ctx: Context, icon: AppIcon) =
        ComponentName(ctx.packageName, "${MainActivity::class.java.name.substringBeforeLast('.')}.${icon.alias}")

    private fun isEnabled(ctx: Context, icon: AppIcon): Boolean = try {
        when (ctx.packageManager.getComponentEnabledSetting(component(ctx, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> icon == AppIcon.ExMusic
        }
    } catch (_: Throwable) {
        icon == AppIcon.ExMusic
    }

    fun init(context: Context) {
        if (inited) return
        inited = true
        val ctx = context.applicationContext
        val on = AppIcon.entries.filter { isEnabled(ctx, it) }
        applied = on.singleOrNull()
        selected = on.firstOrNull() ?: AppIcon.ExMusic
    }

    fun select(icon: AppIcon) {
        selected = icon
    }

    fun apply(context: Context) {
        if (!pending) return
        val ctx = context.applicationContext
        val pm = ctx.packageManager
        val target = selected
        try {
            pm.setComponentEnabledSetting(
                component(ctx, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            AppIcon.entries.filter { it != target }.forEach {
                pm.setComponentEnabledSetting(
                    component(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
                )
            }
            applied = target
        } catch (_: Throwable) {
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconSection() {
    val selected = AppIconSettings.selected
    Text(
        if (AppIconSettings.pending) "Иконка на рабочем столе сменится, как только вы свернёте приложение"
        else "Иконка приложения на рабочем столе и в списке приложений. Меняется, когда вы сворачиваете приложение; лаунчеру может понадобиться пара секунд",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            val isSelected = selected == icon
            Column(
                Modifier.width(96.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { AppIconSettings.select(icon) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                    val shape = RoundedCornerShape(20.dp)
                    Image(
                        painterResource(icon.preview), contentDescription = icon.title,
                        modifier = Modifier.size(68.dp).clip(shape)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                            ),
                        contentScale = ContentScale.Crop,
                    )
                    if (isSelected) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(24.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(R.drawable.ms_check), contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }
                }
                Text(
                    icon.title, Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
