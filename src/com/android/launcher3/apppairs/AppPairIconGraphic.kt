/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.apppairs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable.getDisabledColorFilter
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext

/**
 * A FrameLayout marking the area on an [AppPairIcon] where the visual icon will be drawn. One of
 * two child UI elements on an [AppPairIcon], along with a BubbleTextView holding the text title.
 */
class AppPairIconGraphic @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs), OnDeviceProfileChangeListener {
    private val TAG = "AppPairIconGraphic"

    companion object {
        /** Composes a drawable for this icon, consisting of a background and 2 app icons. */
        @JvmStatic
        fun composeDrawable(appPairInfo: FolderInfo, p: AppPairIconDrawingParams): Drawable {
            // Generate new icons, using themed flag if needed.
            val flags = if (Themes.isThemedIconEnabled(p.context)) BitmapInfo.FLAG_THEMED else 0
            val appIcon1 = appPairInfo.contents[0].newIcon(p.context, flags)
            val appIcon2 = appPairInfo.contents[1].newIcon(p.context, flags)
            appIcon1.setBounds(0, 0, p.memberIconSize.toInt(), p.memberIconSize.toInt())
            appIcon2.setBounds(0, 0, p.memberIconSize.toInt(), p.memberIconSize.toInt())

            // Check disabled status.
            val activity: ActivityContext = ActivityContext.lookupContext(p.context)
            val isLaunchableAtScreenSize =
                activity.deviceProfile.isTablet ||
                    appPairInfo.contents.stream().noneMatch { wii: WorkspaceItemInfo ->
                        wii.hasStatusFlag(WorkspaceItemInfo.FLAG_NON_RESIZEABLE)
                    }
            val shouldDrawAsDisabled = appPairInfo.isDisabled || !isLaunchableAtScreenSize

            // Set disabled status on icons.
            appIcon1.setIsDisabled(shouldDrawAsDisabled)
            appIcon2.setIsDisabled(shouldDrawAsDisabled)

            // Create icon drawable.
            val fullIconDrawable = AppPairIconDrawable(p, appIcon1, appIcon2)
            fullIconDrawable.setBounds(0, 0, p.iconSize, p.iconSize)

            // Set disabled color filter on background paint.
            fullIconDrawable.colorFilter =
                if (shouldDrawAsDisabled) getDisabledColorFilter() else null

            return fullIconDrawable
        }
    }

    private lateinit var parentIcon: AppPairIcon
    private lateinit var drawParams: AppPairIconDrawingParams
    private lateinit var drawable: Drawable

    fun init(icon: AppPairIcon, container: Int) {
        parentIcon = icon
        drawParams = AppPairIconDrawingParams(context, container)
        drawable = composeDrawable(icon.info, drawParams)

        // Center the drawable area in the larger icon canvas
        val lp: LayoutParams = layoutParams as LayoutParams
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.height = drawParams.iconSize
        lp.width = drawParams.iconSize
        layoutParams = lp
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getActivityContext().addOnDeviceProfileChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getActivityContext().removeOnDeviceProfileChangeListener(this)
    }

    private fun getActivityContext(): ActivityContext {
        return ActivityContext.lookupContext(context)
    }

    /** When device profile changes, update orientation */
    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        drawParams.updateOrientation(dp)
        redraw()
    }

    /** Updates the icon drawable and redraws it */
    fun redraw() {
        drawable = composeDrawable(parentIcon.info, drawParams)
        invalidate()
    }

    /**
     * Gets this icon graphic's visual bounds, with respect to the parent icon's coordinate system.
     */
    fun getIconBounds(outBounds: Rect) {
        outBounds.set(0, 0, drawParams.backgroundSize.toInt(), drawParams.backgroundSize.toInt())

        outBounds.offset(
            // x-coordinate in parent's coordinate system
            ((parentIcon.width - drawParams.backgroundSize) / 2).toInt(),
            // y-coordinate in parent's coordinate system
            (parentIcon.paddingTop + drawParams.standardIconPadding + drawParams.outerPadding)
                .toInt()
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawable.draw(canvas)
    }
}
