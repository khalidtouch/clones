package com.reel.simplewebview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.webkit.WebView

import androidx.core.view.*
import androidx.core.widget.ScrollerCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.properties.Delegates

class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr),
    NestedScrollingChild, NestedScrollingParent {
    companion object {
        private const val INVALID_POINTER: Int = -1
        private const val TAG: String = "NestedWebView"
    }

    private lateinit var scrollingChildHelper: NestedScrollingChildHelper
    private var isBeingDragged: Boolean = false
    private var velocityTracker: VelocityTracker? = null
    private var touchSlop by Delegates.notNull<Int>()
    private var activePointerId = INVALID_POINTER
    private var nestedYOffset by Delegates.notNull<Float>()
    private var scroller: ScrollerCompat
    private var minVelocity by Delegates.notNull<Float>()
    private var maxVelocity by Delegates.notNull<Float>()
    private var lastMotionY by Delegates.notNull<Int>()

    private var scrollOffset: Array<Int> = Array(2) { 0 }
    private var scrollConsumed: Array<Int> = Array(2) { 0 }

    init {
        scroller = ScrollerCompat.create(context, null)
        val viewConfig = ViewConfiguration.get(context)
        touchSlop = viewConfig.scaledTouchSlop
        minVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
        maxVelocity = viewConfig.scaledMaximumFlingVelocity.toFloat()

        scrollingChildHelper = NestedScrollingChildHelper(this)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        initializeVelocityTrackerIfNotExists()
        val motionEvent = MotionEvent.obtain(event)
        val actionMasked = MotionEventCompat.getActionMasked(event)

        if (actionMasked == MotionEvent.ACTION_DOWN) nestedYOffset = 0F

        motionEvent.offsetLocation(0F, nestedYOffset)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isBeingDragged != scroller.isFinished) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }

                lastMotionY = motionEvent.y.toInt()
                activePointerId = motionEvent.getPointerId(0)
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }
            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = motionEvent.findPointerIndex(activePointerId)
                if (activePointerIndex == -1) Log.e(TAG, "onTouchEvent: Invalid Pointer")

                val y = motionEvent.getY(activePointerIndex)
                var deltaY = lastMotionY - y
                if (dispatchNestedPreScroll(
                        0,
                        deltaY.toInt(),
                        scrollConsumed.toIntArray(),
                        scrollOffset.toIntArray()
                    )
                ) {
                    deltaY -= scrollConsumed[1]
                    motionEvent.offsetLocation(0F, scrollOffset[1].toFloat())
                    nestedYOffset += scrollOffset[1]
                }

                if (!isBeingDragged && abs(deltaY) > touchSlop) { //if no dragging
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isBeingDragged = true
                    if (deltaY > 0) {
                        deltaY -= touchSlop
                    } else {
                        deltaY += touchSlop
                    }

                }

                if (isBeingDragged) {
                    lastMotionY = (y - scrollOffset[1]).toInt()
                    val oldY = scrollY
                    val scrolledDeltaY = scrollY - oldY
                    val unconsumedY = deltaY - scrolledDeltaY

                    if (dispatchNestedScroll(
                            0,
                            scrolledDeltaY,
                            0,
                            unconsumedY.toInt(),
                            scrollOffset.toIntArray()
                        )
                    ) {
                        lastMotionY -= scrollOffset[1]
                        motionEvent.offsetLocation(0F, scrollOffset[1].toFloat())
                        nestedYOffset += scrollOffset[1]
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isBeingDragged) {
                    val velocityTracker = velocityTracker
                    velocityTracker?.computeCurrentVelocity(1000, maxVelocity)
                    var initialVelocity = VelocityTrackerCompat.getYVelocity(
                        velocityTracker,
                        activePointerId
                    )

                    if (abs(initialVelocity) > minVelocity) {
                        flingWithNestedDispatch(-initialVelocity)
                    } else if (scroller.springBack(
                            scrollX,
                            scrollY,
                            0,
                            0,
                            0,
                            scrollRange()
                        )
                    ) {
                        ViewCompat.postInvalidateOnAnimation(this)
                    }
                }
                activePointerId = INVALID_POINTER
                endDrag()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isBeingDragged && childCount > 0) {
                    if (scroller.springBack(scrollX, scrollY, 0, 0, 0, scrollRange())) {
                        ViewCompat.postInvalidateOnAnimation(this)
                    }
                }
                activePointerId = INVALID_POINTER
                endDrag()
            }

            MotionEventCompat.ACTION_POINTER_DOWN -> {
                val index = MotionEventCompat.getActionIndex(motionEvent)
                lastMotionY = motionEvent.getY(index).toInt()
                activePointerId = motionEvent.getPointerId(index)
            }

            MotionEventCompat.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(motionEvent)
                lastMotionY =
                    motionEvent.getY(motionEvent.findPointerIndex(activePointerId)).toInt()
            }
        }

        velocityTracker?.addMovement(motionEvent)
        motionEvent.recycle()
        return super.onTouchEvent(event)
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent?) {
        val pointerIndex =
            (motionEvent?.action?.and(MotionEventCompat.ACTION_POINTER_INDEX_MASK))?.shr(
                MotionEventCompat.ACTION_POINTER_INDEX_SHIFT
            )
        val pointerId = motionEvent?.getPointerId(pointerIndex!!)
        if (pointerId == activePointerId) {
            val newPointerIndex = if (pointerIndex == 0) 1 else 0
            lastMotionY = motionEvent.getY(newPointerIndex).toInt()
            activePointerId = motionEvent.getPointerId(newPointerIndex)
            velocityTracker?.clear()
        }
    }

    private fun scrollRange(): Int {
        return computeVerticalScrollRange()
    }

    private fun endDrag() {
        isBeingDragged = false
        recycleVelocityTracker()
        stopNestedScroll()
    }

    private fun flingWithNestedDispatch(velocityY: Float) {
        val scrollY = scrollY
        val canFling = (scrollY > 0 || velocityY > 0)
                && (scrollY < scrollRange() || velocityY < 0)
        if (!dispatchNestedPreFling(0F, velocityY)) {
            dispatchNestedFling(0F, velocityY, canFling)
            if (canFling) fling(velocityY)
        }
    }

    private fun fling(velocityY: Float) {
        if (childCount > 0) {
            var height = height - paddingBottom - paddingTop
            var bottom = getChildAt(0).height

            scroller.fling(
                scrollX,
                scrollY,
                0,
                velocityY.toInt(),
                0,
                0,
                0,
                max(0, bottom - height),
                0,
                height / 2
            )

            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun initializeVelocityTrackerIfNotExists() {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        val action = ev?.action
        if (action == MotionEvent.ACTION_MOVE && isBeingDragged) return true

        when (action?.and(MotionEventCompat.ACTION_MASK)) {
            MotionEvent.ACTION_MOVE -> {
                val activePointerId = activePointerId
                if (activePointerId == INVALID_POINTER) {

                }

                val pointerIndex = ev?.findPointerIndex(activePointerId)
                if (pointerIndex == -1) Log.e(TAG, "onInterceptTouchEvent: Invalid PointerId")

                val y = ev.getY(pointerIndex)
                val yDiff = abs(y - lastMotionY)
                if (yDiff > touchSlop && (nestedScrollAxes.and(ViewCompat.SCROLL_AXIS_VERTICAL) == 0)) {
                    isBeingDragged = true
                    lastMotionY = y.toInt()
                    initializeVelocityTrackerIfNotExists()
                    velocityTracker?.addMovement(ev)
                    nestedYOffset = 0F
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

            }

            MotionEvent.ACTION_DOWN -> {
                val y = ev.y
                lastMotionY = y.toInt()
                activePointerId = ev.getPointerId(0)

                initOrResetVelocityTracker()
                velocityTracker?.addMovement(ev)

                scroller?.computeScrollOffset()
                isBeingDragged = !scroller.isFinished
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                isBeingDragged = false
                activePointerId = INVALID_POINTER
                recycleVelocityTracker()
                if (scroller.springBack(scrollX, scrollY, 0, 0, 0, scrollRange())) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                stopNestedScroll()
            }

            MotionEventCompat.ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
            }

        }
        return isBeingDragged
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun initOrResetVelocityTracker() {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        else velocityTracker?.clear()
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return scrollingChildHelper.isNestedScrollingEnabled
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        scrollingChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return scrollingChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll() {
        scrollingChildHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean {
        return scrollingChildHelper.hasNestedScrollingParent()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean {
        return scrollingChildHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return scrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }


    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return scrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return scrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun getNestedScrollAxes(): Int {
        return ViewCompat.SCROLL_AXIS_NONE
    }
}