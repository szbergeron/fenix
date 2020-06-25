package org.mozilla.fenix.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class GestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var gestureHandler: ToolbarGestureHandler? = null

    fun setupGestureHandler(gestureHandler: ToolbarGestureHandler) {
        this.gestureHandler = gestureHandler
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (super.onTouchEvent(event)) {
            true
        } else {
            gestureHandler?.onTouchEvent(event) ?: false
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return if (super.onInterceptTouchEvent(event)) {
            true
        } else {
            gestureHandler?.onInterceptTouchEvent(event) ?: false
        }
    }
}
