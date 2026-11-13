package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// The same full-size shuffle arrows are used in both modes. The two stars
// fit INSIDE the unchanged 24 x 24dp bounds, with no scaling on mode changes.
@Suppress("CheckReturnValue")
val SimpIcons.Shuffle: ImageVector
    get() {
        if (_Shuffle == null) {
            _Shuffle = ImageVector.Builder(
                name = "Shuffle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
          path(fill = SolidColor(Color.Black)) {
            moveTo(20.235f, 9.39f)
            lineTo(16.455f, 7.369f)
            lineTo(16.455f, 8.564f)
            lineTo(15.096f, 8.616f)
            lineTo(13.854f, 9.029f)
            lineTo(12.531f, 9.863f)
            lineTo(10.893f, 11.248f)
            lineTo(8.94f, 9.657f)
            lineTo(7.275f, 8.788f)
            lineTo(6.006f, 8.564f)
            lineTo(3.657f, 8.625f)
            lineTo(3.324f, 8.865f)
            lineTo(3.126f, 9.313f)
            lineTo(3.225f, 9.777f)
            lineTo(3.513f, 10.078f)
            lineTo(3.909f, 10.216f)
            lineTo(6.141f, 10.224f)
            lineTo(6.879f, 10.413f)
            lineTo(8.175f, 11.162f)
            lineTo(9.651f, 12.4f)
            lineTo(8.175f, 13.638f)
            lineTo(6.879f, 14.387f)
            lineTo(6.141f, 14.576f)
            lineTo(3.585f, 14.679f)
            lineTo(3.189f, 15.092f)
            lineTo(3.189f, 15.728f)
            lineTo(3.513f, 16.098f)
            lineTo(3.909f, 16.236f)
            lineTo(6.006f, 16.236f)
            lineTo(6.969f, 16.107f)
            lineTo(8.697f, 15.315f)
            lineTo(10.893f, 13.552f)
            lineTo(12.531f, 14.937f)
            lineTo(13.854f, 15.771f)
            lineTo(15.096f, 16.184f)
            lineTo(16.455f, 16.236f)
            lineTo(16.455f, 17.431f)
            lineTo(20.235f, 15.41f)
            lineTo(16.455f, 13.389f)
            lineTo(16.455f, 14.584f)
            lineTo(15.033f, 14.464f)
            lineTo(13.827f, 13.802f)
            lineTo(12.144f, 12.4f)
            lineTo(13.827f, 10.998f)
            lineTo(15.033f, 10.336f)
            lineTo(16.455f, 10.216f)
            lineTo(16.455f, 11.411f)
            close()
          }
            }.buildSimpIcon()
        }
        return _Shuffle!!
    }

private var _Shuffle: ImageVector? = null
