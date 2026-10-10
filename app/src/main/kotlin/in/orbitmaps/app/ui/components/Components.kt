// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import `in`.orbitmaps.app.R
import `in`.orbitmaps.app.ui.sample.Status
import `in`.orbitmaps.app.ui.theme.OrbitColors

/** Shows a preview in both light and dark themes (plan step 12.4: every window in light and dark). */
@Preview(name = "Light", showBackground = true, widthDp = 360, heightDp = 760)
@Preview(
    name = "Dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class ThemePreviews

/** A full page with a back button and a title (search, forms and the profile pages). */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                actions()
            }
            val body = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
            Column(
                modifier = if (scrollable) body.verticalScroll(rememberScrollState()) else body,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }
    )
}

/** A tappable row with an icon, a title, an optional subtitle and optional trailing content. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).then(clickable).padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

/** A row with a switch; the whole row toggles, for large touch targets and TalkBack. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The small coloured label for places and reports: green Confirmed, amber New, sky blue Compiled. */
@Composable
fun StatusTag(status: Status, modifier: Modifier = Modifier, confirmations: Int = 0) {
    val (color, text) = when (status) {
        Status.New -> OrbitColors.Amber to stringResource(R.string.tag_new)
        Status.Confirmed -> OrbitColors.Confirmed to if (confirmations > 0) {
            pluralStringResource(R.plurals.tag_confirmed_by, confirmations, confirmations)
        } else {
            stringResource(R.string.tag_confirmed)
        }
        Status.Compiled -> OrbitColors.Sky to stringResource(R.string.tag_compiled)
    }
    Tag(text = text, color = color, modifier = modifier)
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** A horizontally scrolling row of chips or buttons. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        content = content
    )
}

/** A round badge with a person's initials, used for group members and chat. */
@Composable
fun InitialsBadge(name: String, modifier: Modifier = Modifier, color: Color = OrbitColors.MemberBlue) {
    val initials = name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(40.dp).background(color, CircleShape)) {
        Text(initials, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Says the window shows sample data, so testers don't mistake it for a working feature. */
@Composable
fun SampleDataNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.sample_data_note),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 4.dp))
}

/** Stands in for the MapLibre map in previews, where the native map cannot render. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier, night: Boolean = false) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (night) OrbitColors.PlaceholderLandNight else OrbitColors.PlaceholderLand)
    )
}
