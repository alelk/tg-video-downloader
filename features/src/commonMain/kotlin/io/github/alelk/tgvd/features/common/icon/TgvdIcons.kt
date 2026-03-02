package io.github.alelk.tgvd.features.common.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object TgvdIcons {

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 18f); lineTo(5f, 18f); close()
                moveTo(19f, 9f); lineTo(15f, 9f); lineTo(15f, 3f); lineTo(9f, 3f); lineTo(9f, 9f)
                lineTo(5f, 9f); lineTo(12f, 16f); close()
            }
        }.build()
    }

    val List: ImageVector by lazy {
        ImageVector.Builder(
            name = "List",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 13f); lineTo(5f, 13f); lineTo(5f, 11f); lineTo(3f, 11f); close()
                moveTo(3f, 17f); lineTo(5f, 17f); lineTo(5f, 15f); lineTo(3f, 15f); close()
                moveTo(3f, 9f); lineTo(5f, 9f); lineTo(5f, 7f); lineTo(3f, 7f); close()
                moveTo(7f, 13f); lineTo(21f, 13f); lineTo(21f, 11f); lineTo(7f, 11f); close()
                moveTo(7f, 17f); lineTo(21f, 17f); lineTo(21f, 15f); lineTo(7f, 15f); close()
                moveTo(7f, 7f); lineTo(7f, 9f); lineTo(21f, 9f); lineTo(21f, 7f); close()
            }
        }.build()
    }

    val Rule: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rule",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.54f, 11f); lineTo(13f, 7.46f); lineTo(14.41f, 6.05f)
                lineTo(16.53f, 8.17f); lineTo(20.77f, 3.93f); lineTo(22.18f, 5.34f); close()
                moveTo(11f, 7f); lineTo(2f, 7f); lineTo(2f, 9f); lineTo(11f, 9f); close()
                moveTo(21f, 13.41f); lineTo(19.59f, 12f); lineTo(17f, 14.59f)
                lineTo(14.41f, 12f); lineTo(13f, 13.41f); lineTo(15.59f, 16f)
                lineTo(13f, 18.59f); lineTo(14.41f, 20f); lineTo(17f, 17.41f)
                lineTo(19.59f, 20f); lineTo(21f, 18.59f); lineTo(18.41f, 16f); close()
                moveTo(11f, 15f); lineTo(2f, 15f); lineTo(2f, 17f); lineTo(11f, 17f); close()
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            addPath(
                pathData = addPathNodes("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 11f); lineTo(7.83f, 11f); lineTo(13.42f, 5.41f); lineTo(12f, 4f)
                lineTo(4f, 12f); lineTo(12f, 20f); lineTo(13.41f, 18.59f); lineTo(7.83f, 13f)
                lineTo(20f, 13f); close()
            }
        }.build()
    }

    val Add: ImageVector by lazy {
        ImageVector.Builder(
            name = "Add",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(19f, 13f); lineTo(13f, 13f); lineTo(13f, 19f); lineTo(11f, 19f)
                lineTo(11f, 13f); lineTo(5f, 13f); lineTo(5f, 11f); lineTo(11f, 11f)
                lineTo(11f, 5f); lineTo(13f, 5f); lineTo(13f, 11f); lineTo(19f, 11f); close()
            }
        }.build()
    }

    val Edit: ImageVector by lazy {
        ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17.25f); lineTo(3f, 21f); lineTo(6.75f, 21f); lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f); lineTo(3f, 17.25f); close()
                moveTo(20.71f, 7.04f); lineTo(18.37f, 4.7f); lineTo(16.96f, 3.29f)
                lineTo(15.13f, 5.12f); lineTo(18.88f, 8.87f); lineTo(20.71f, 7.04f); close()
            }
        }.build()
    }

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f); lineTo(6f, 7f); lineTo(18f, 7f); lineTo(18f, 19f)
                lineTo(16f, 21f); lineTo(8f, 21f); close()
                moveTo(19f, 4f); lineTo(15.5f, 4f); lineTo(14.5f, 3f); lineTo(9.5f, 3f)
                lineTo(8.5f, 4f); lineTo(5f, 4f); lineTo(5f, 6f); lineTo(19f, 6f); close()
            }
        }.build()
    }
}

