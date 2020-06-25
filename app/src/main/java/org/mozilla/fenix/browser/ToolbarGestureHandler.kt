package org.mozilla.fenix.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.fragment_browser.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.support.ktx.android.view.getRectWithViewLocation
import org.mozilla.fenix.ext.sessionsOfType
import org.mozilla.fenix.ext.settings
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Handles intercepting touch events on the toolbar for swipe gestures and executes the
 * necessary animations.
 */
@Suppress("LargeClass", "TooManyFunctions")
class ToolbarGestureHandler(
    private val activity: Activity,
    private val container: GestureLayout,
    private val toolbarLayout: View,
    private val sessionManager: SessionManager
) : LayoutContainer {

    private enum class GestureDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    private sealed class Destination {
        data class Tab(val session: Session) : Destination()
        object None : Destination()
    }

    override val containerView: View?
        get() = container

    private var windowInsets: WindowInsetsCompat? = null

    private val windowWidth: Int = with(DisplayMetrics()) {
        activity.windowManager.defaultDisplay.getMetrics(this)
        this.widthPixels
    }

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private var gestureStart = PointF()
    private var gestureDirection = GestureDirection.LEFT_TO_RIGHT
    private val defaultVelocity = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        DP_PER_SECOND,
        activity.resources.displayMetrics
    )

    fun setup() {
        CoroutineScope(Main).launch {
            windowInsets = getInsets()
        }
        container.setupGestureHandler(this)
    }

    fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                val destination = getDestination()
                when {
                    destination is Destination.Tab && isGestureComplete() -> animateToNextTab(
                        destination.session
                    )
                    else -> animateCanceledGesture()
                }
                false
            }
            MotionEvent.ACTION_MOVE -> {
                // Reset the start of the gesture if the tab preview is completely off screen
                // so the user doesn't have to drag their finger to the original gesture start
                // coordinate to trigger the animation again
                @Suppress("ComplexCondition")
                if ((gestureDirection == GestureDirection.RIGHT_TO_LEFT && event.x > gestureStart.x) ||
                    (gestureDirection == GestureDirection.LEFT_TO_RIGHT && event.x < gestureStart.x)
                ) {
                    gestureStart.x = event.x
                }

                when (getDestination()) {
                    is Destination.Tab -> {
                        tabPreview.translationX = when (gestureDirection) {
                            GestureDirection.RIGHT_TO_LEFT -> windowWidth - (gestureStart.x - event.x)
                            GestureDirection.LEFT_TO_RIGHT -> event.x - gestureStart.x - windowWidth
                        }
                        browserLayout.translationX = event.x - gestureStart.x
                    }
                    is Destination.None -> {
                        // If there is no "next" tab to swipe to in the gesture direction, only do a
                        // partial animation to show that we are at the end of the tab list
                        val visibleContentWidth =
                            browserLayout.getRectWithViewLocation().visibleWidth.toDouble()
                        if (visibleContentWidth / windowWidth >= OVERSCROLL_VIEW_PERCENT) {
                            browserLayout.translationX = event.x - gestureStart.x
                        }
                    }
                }
                true
            }
            else -> false
        }
    }

    @Suppress("NestedBlockDepth")
    fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStart = PointF(event.x, event.y)
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val xDiff = (event.x - gestureStart.x).toInt()
                val yDiff = (event.y - gestureStart.y).toInt()

                if (!gestureStart.isInToolbar()) {
                    false
                } else if (abs(xDiff) > touchSlop && abs(yDiff) < toolbarLayout.height) {
                    // If the user has dragged her finger horizontally more than the touch slop
                    // and hasn't dragged their finger straight up too far start the scroll
                    gestureDirection = if (xDiff > 0) {
                        GestureDirection.LEFT_TO_RIGHT
                    } else {
                        GestureDirection.RIGHT_TO_LEFT
                    }
                    preparePreview(getDestination())
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun createFlingAnimation(
        view: View,
        minValue: Float,
        maxValue: Float,
        startVelocity: Float
    ): FlingAnimation =
        FlingAnimation(view, DynamicAnimation.TRANSLATION_X).apply {
            setMinValue(minValue)
            setMaxValue(maxValue)
            setStartVelocity(startVelocity)
            friction = ANIMATION_FRICTION
        }

    private fun getDestination(): Destination {
        val currentSession = sessionManager.selectedSession ?: return Destination.None
        val currentIndex = sessionManager.sessionsOfType(currentSession.private).indexOfFirst {
            it.id == currentSession.id
        }

        return if (currentIndex == -1) {
            Destination.None
        } else {
            val sessions = sessionManager.sessionsOfType(currentSession.private)
            val index = when (gestureDirection) {
                GestureDirection.RIGHT_TO_LEFT -> currentIndex + 1
                GestureDirection.LEFT_TO_RIGHT -> currentIndex - 1
            }

            if (index < sessions.count() && index >= 0) {
                Destination.Tab(sessions.elementAt(index))
            } else {
                Destination.None
            }
        }
    }

    private fun preparePreview(destination: Destination) {
        val xCoordinate = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> windowWidth
            GestureDirection.LEFT_TO_RIGHT -> -windowWidth
        }
        val thumbnailId = when (destination) {
            is Destination.Tab -> destination.session.id
            is Destination.None -> return
        }

        tabPreview.loadPreviewThumbnail(thumbnailId)
        tabPreview.alpha = 1f
        tabPreview.translationX = xCoordinate.toFloat()
        tabPreview.isVisible = true
    }

    private fun isGestureComplete(): Boolean =
        tabPreview.getRectWithViewLocation().visibleWidth.toDouble() / windowWidth >= GESTURE_FINISH_PERCENT

    private fun animateToNextTab(session: Session) {
        val browserFinalXCoordinate: Float = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> -windowWidth.toFloat()
            GestureDirection.LEFT_TO_RIGHT -> windowWidth.toFloat()
        }
        val animationVelocity = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> -defaultVelocity
            GestureDirection.LEFT_TO_RIGHT -> defaultVelocity
        }

        // Finish animating the browserLayout off screen and tabPreview on screen
        createFlingAnimation(
            view = browserLayout,
            minValue = min(0f, browserFinalXCoordinate),
            maxValue = max(0f, browserFinalXCoordinate),
            startVelocity = animationVelocity
        ).addUpdateListener { _, value, _ ->
            tabPreview.translationX = when (gestureDirection) {
                GestureDirection.RIGHT_TO_LEFT -> value + windowWidth
                GestureDirection.LEFT_TO_RIGHT -> value - windowWidth
            }
        }.addEndListener { _, _, _, _ ->
            browserLayout.translationX = 0f
            sessionManager.select(session)

            // Fade out the tab preview to prevent flickering
            val shortAnimationDuration =
                container.resources.getInteger(android.R.integer.config_shortAnimTime)
            tabPreview.animate()
                .alpha(0f)
                .setDuration(shortAnimationDuration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        tabPreview.isVisible = false
                    }
                }).start()
        }.start()
    }

    private fun animateCanceledGesture() {
        val velocity = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> defaultVelocity
            GestureDirection.LEFT_TO_RIGHT -> -defaultVelocity
        }

        createFlingAnimation(
            view = browserLayout,
            minValue = min(0f, browserLayout.translationX),
            maxValue = max(0f, browserLayout.translationX),
            startVelocity = velocity
        ).addUpdateListener { _, value, _ ->
            tabPreview.translationX = when (gestureDirection) {
                GestureDirection.RIGHT_TO_LEFT -> value + windowWidth
                GestureDirection.LEFT_TO_RIGHT -> value - windowWidth
            }
        }.addEndListener { _, _, _, _ ->
            tabPreview.isVisible = false
        }.start()
    }

    private suspend fun getInsets(): WindowInsetsCompat? = suspendCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.window.decorView.doOnAttach {
                cont.resume(WindowInsetsCompat.toWindowInsetsCompat(it.rootWindowInsets))
            }
        } else {
            cont.resume(null)
        }
    }

    private fun PointF.isInToolbar(): Boolean {
        val toolbarLocation = toolbarLayout.getRectWithViewLocation()
        // In Android 10, the system gesture touch area overlaps the bottom of the toolbar, so
        // lets make our swipe area taller by that amount
        windowInsets?.let { insets ->
            if (activity.settings().shouldUseBottomToolbar) {
                toolbarLocation.top -= (insets.systemGestureInsets.bottom - insets.stableInsetBottom)
            }
        }
        return toolbarLocation.contains(toPoint())
    }

    private val Rect.visibleWidth: Int
        get() = if (left < 0) {
            right
        } else {
            windowWidth - left
        }

    companion object {
        /**
         * The percentage of the tab preview that needs to be visible to consider the
         * tab switching gesture complete.
         */
        private const val GESTURE_FINISH_PERCENT = 0.25

        /**
         * The minimum percentage of the browser view that will be shown when the tab switching
         * gesture is attempted with no destination available to switch to
         */
        private const val OVERSCROLL_VIEW_PERCENT = 0.80

        /**
         * The speed of the fling animation.
         */
        private const val DP_PER_SECOND = 1250f

        /**
         * The friction applied to the fling animation.
         */
        private const val ANIMATION_FRICTION = 0.1f
    }
}
