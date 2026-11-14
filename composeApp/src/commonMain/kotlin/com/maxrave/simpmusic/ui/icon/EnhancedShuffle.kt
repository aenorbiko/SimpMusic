package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Same 92% shuffle arrow geometry as Shuffle; bigger top star and centered bottom dot.
@Suppress("CheckReturnValue")
val SimpIcons.EnhancedShuffle: ImageVector
  get() {
    if (_EnhancedShuffle != null) {
      return _EnhancedShuffle!!
    }
    _EnhancedShuffle =
      ImageVector.Builder(
          name = "EnhancedShuffle",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          group(scaleX = 0.92f, scaleY = 0.92f, pivotX = 12f, pivotY = 12f) {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(15f, 20f)
            quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
            quadTo(14f, 19.43f, 14f, 19f)
            reflectiveQuadToRelative(0.29f, -0.71f)
            reflectiveQuadTo(15f, 18f)
            horizontalLineToRelative(1.6f)
            lineTo(14.13f, 15.53f)
            quadToRelative(-0.3f, -0.3f, -0.29f, -0.71f)
            reflectiveQuadTo(14.15f, 14.1f)
            reflectiveQuadToRelative(0.71f, -0.3f)
            reflectiveQuadToRelative(0.71f, 0.3f)
            lineTo(18f, 16.55f)
            verticalLineTo(15f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(19f, 14f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(20f, 15f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(19f, 20f)
            horizontalLineTo(15f)
            close()
            moveTo(4.3f, 19.7f)
            quadTo(4.03f, 19.43f, 4.03f, 19f)
            reflectiveQuadTo(4.3f, 18.3f)
            lineTo(16.6f, 6f)
            horizontalLineTo(15f)
            quadTo(14.58f, 6f, 14.29f, 5.71f)
            quadTo(14f, 5.43f, 14f, 5f)
            reflectiveQuadTo(14.29f, 4.29f)
            reflectiveQuadTo(15f, 4f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 5f)
            verticalLineTo(9f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(19f, 10f)
            reflectiveQuadTo(18.29f, 9.71f)
            reflectiveQuadTo(18f, 9f)
            verticalLineTo(7.4f)
            lineTo(5.7f, 19.7f)
            quadTo(5.43f, 19.98f, 5f, 19.98f)
            quadToRelative(-0.42f, 0f, -0.7f, -0.28f)
            close()
            moveTo(4.28f, 5.7f)
            quadTo(4f, 5.43f, 4f, 5f)
            reflectiveQuadTo(4.28f, 4.3f)
            reflectiveQuadTo(4.96f, 4.02f)
            reflectiveQuadTo(5.68f, 4.3f)
            lineToRelative(4.2f, 4.18f)
            quadToRelative(0.28f, 0.28f, 0.29f, 0.69f)
            reflectiveQuadTo(9.88f, 9.88f)
            quadTo(9.6f, 10.15f, 9.18f, 10.15f)
            reflectiveQuadTo(8.48f, 9.88f)
            lineTo(4.28f, 5.7f)
            close()
          }
          }
          // One prominent sparkle at upper-left, not overlapping the arrows.
          path(fill = SolidColor(Color.Black)) {
            moveTo(9.6f, 0.2f)
            lineTo(11.05f, 2.15f)
            lineTo(13.2f, 3.35f)
            lineTo(11.05f, 4.55f)
            lineTo(9.6f, 6.5f)
            lineTo(8.15f, 4.55f)
            lineTo(6f, 3.35f)
            lineTo(8.15f, 2.15f)
            close()
          }
          // Small four-point sparkle; its centre stays EXACTLY at the
          // previous dot centre (11f, 17.8f) in the 24dp viewport.
          path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 16.48f)
            lineTo(11.61f, 17.3f)
            lineTo(12.51f, 17.8f)
            lineTo(11.61f, 18.3f)
            lineTo(11f, 19.12f)
            lineTo(10.39f, 18.3f)
            lineTo(9.49f, 17.8f)
            lineTo(10.39f, 17.3f)
            close()
          }
        }
        .buildSimpIcon()
    return _EnhancedShuffle!!
  }

private var _EnhancedShuffle: ImageVector? = null
