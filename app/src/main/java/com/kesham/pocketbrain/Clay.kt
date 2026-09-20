package com.kesham.pocketbrain

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kesham.pocketbrain.ui.theme.ClayShadowDark
import com.kesham.pocketbrain.ui.theme.ClayShadowLight
import com.kesham.pocketbrain.ui.theme.ClaySurface

val ClayCardShape = RoundedCornerShape(28.dp)
val ClayPillShape = RoundedCornerShape(999.dp)

private val ShadowOffset = 6.dp
private val ShadowBlur = 12.dp
private val FallbackElevation = 6.dp

// Modifier.blur is a no-op below API 31, which would leave hard-edged rectangles poking out from
// behind the surface instead of a soft halo.
private val supportsLayeredShadows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * An opaque, raised "clay" surface: a light halo offset toward the top-left and a darker one
 * toward the bottom-right, both blurred, over a fully rounded shape.
 */
@Composable
fun ClayBox(
    modifier: Modifier = Modifier,
    shape: Shape = ClayCardShape,
    color: Color = ClaySurface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        if (supportsLayeredShadows) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = -ShadowOffset, y = -ShadowOffset)
                    .blur(ShadowBlur, BlurredEdgeTreatment.Unbounded)
                    .background(ClayShadowLight, shape)
            )
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = ShadowOffset, y = ShadowOffset)
                    .blur(ShadowBlur, BlurredEdgeTreatment.Unbounded)
                    .background(ClayShadowDark, shape)
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(color, shape)
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .shadow(FallbackElevation, shape)
                    .background(color, shape)
            )
        }

        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
