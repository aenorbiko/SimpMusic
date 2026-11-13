package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// The same full-size shuffle arrows are used in both modes. The two stars
// fit INSIDE the unchanged 24 x 24dp bounds, with no scaling on mode changes.
@Suppress("CheckReturnValue")
val SimpIcons.EnhancedShuffle: ImageVector
    get() {
        if (_EnhancedShuffle == null) {
            _EnhancedShuffle = ImageVector.Builder(
                name = "EnhancedShuffle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
          path(fill = SolidColor(Color.Black)) {
            moveTo(21.15f, 8.9f)
            lineTo(16.95f, 6.55f)
            lineTo(16.95f, 7.94f)
            lineTo(15.44f, 8f)
            lineTo(14.06f, 8.48f)
            lineTo(12.59f, 9.45f)
            lineTo(10.77f, 11.06f)
            lineTo(8.6f, 9.21f)
            lineTo(6.75f, 8.2f)
            lineTo(5.34f, 7.94f)
            lineTo(2.73f, 8.01f)
            lineTo(2.36f, 8.29f)
            lineTo(2.14f, 8.81f)
            lineTo(2.25f, 9.35f)
            lineTo(2.57f, 9.7f)
            lineTo(3.01f, 9.86f)
            lineTo(5.49f, 9.87f)
            lineTo(6.31f, 10.09f)
            lineTo(7.75f, 10.96f)
            lineTo(9.39f, 12.4f)
            lineTo(7.75f, 13.84f)
            lineTo(6.31f, 14.71f)
            lineTo(5.49f, 14.93f)
            lineTo(2.65f, 15.05f)
            lineTo(2.21f, 15.53f)
            lineTo(2.21f, 16.27f)
            lineTo(2.57f, 16.7f)
            lineTo(3.01f, 16.86f)
            lineTo(5.34f, 16.86f)
            lineTo(6.41f, 16.71f)
            lineTo(8.33f, 15.79f)
            lineTo(10.77f, 13.74f)
            lineTo(12.59f, 15.35f)
            lineTo(14.06f, 16.32f)
            lineTo(15.44f, 16.8f)
            lineTo(16.95f, 16.86f)
            lineTo(16.95f, 18.25f)
            lineTo(21.15f, 15.9f)
            lineTo(16.95f, 13.55f)
            lineTo(16.95f, 14.94f)
            lineTo(15.37f, 14.8f)
            lineTo(14.03f, 14.03f)
            lineTo(12.16f, 12.4f)
            lineTo(14.03f, 10.77f)
            lineTo(15.37f, 10f)
            lineTo(16.95f, 9.86f)
            lineTo(16.95f, 11.25f)
            close()
          }
          path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 1.57f)
            lineTo(12.8f, 3.15f)
            lineTo(14.2f, 3.95f)
            lineTo(12.8f, 4.75f)
            lineTo(12f, 6.33f)
            lineTo(11.2f, 4.75f)
            lineTo(9.8f, 3.95f)
            lineTo(11.2f, 3.15f)
            close()
          }
          path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 17.67f)
            lineTo(12.8f, 19.25f)
            lineTo(14.2f, 20.05f)
            lineTo(12.8f, 20.85f)
            lineTo(12f, 22.43f)
            lineTo(11.2f, 20.85f)
            lineTo(9.8f, 20.05f)
            lineTo(11.2f, 19.25f)
            close()
          }
            }.buildSimpIcon()
        }
        return _EnhancedShuffle!!
    }

private var _EnhancedShuffle: ImageVector? = null
