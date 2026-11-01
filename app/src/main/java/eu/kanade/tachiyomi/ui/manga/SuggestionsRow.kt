package eu.kanade.tachiyomi.ui.manga

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga

// KMK -->

/** A manga of the same source found by searching for the keywords of the current title. */
data class SourceSuggestion(
    val manga: SManga,
)

/** Related manga of the source as a row of covers, like the related manga of Komikku's manga screen. */
@Composable
fun SuggestionsRow(
    suggestions: List<SourceSuggestion>,
    onOpen: (SManga) -> Unit,
    onSeeAll: () -> Unit,
) {
    // The header is hosted in a plain ComposeView, so content colors have to be provided here
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.suggestions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.related_manga),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { it.manga.url }) { suggestion ->
                    Column(
                        modifier = Modifier.width(96.dp).clickable { onOpen(suggestion.manga) },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AndroidView(
                            factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                            update = { view ->
                                suggestion.manga.thumbnail_url?.let { view.load(it) } ?: view.setImageDrawable(null)
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            suggestion.manga.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
// KMK <--
