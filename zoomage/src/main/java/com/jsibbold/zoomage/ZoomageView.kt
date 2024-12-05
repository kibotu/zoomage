/**
 * Copyright 2016 Jeffrey Sibbold
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jsibbold.zoomage

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ScaleGestureDetectorCompat
import com.jsibbold.zoomage.AutoResetMode.Parser.fromInt
import com.jsibbold.zoomage.ZoomageView.SimpleAnimatorListener

/**
 * ZoomageView is a pinch-to-zoom extension of [ImageView], providing a smooth
 * user experience and a very natural feel when zooming and translating. It also supports
 * automatic resetting, and allows for exterior bounds restriction to keep the image within
 * visible window.
 */
open class ZoomageView : AppCompatImageView, OnScaleGestureListener {
    private val RESET_DURATION = 200

    private var startScaleType: ScaleType? = null

    // These matrices will be used to move and zoom image
    private val matrix = Matrix()
    private var startMatrix = Matrix()

    private val matrixValues = FloatArray(9)
    private var startValues: FloatArray? = null

    private var minScale = MIN_SCALE
    private var maxScale = MAX_SCALE

    //the adjusted scale bounds that account for an image's starting scale values
    private var calculatedMinScale = MIN_SCALE
    private var calculatedMaxScale = MAX_SCALE

    private val bounds = RectF()

    private var translatable = false
    private var zoomable = false
    private var doubleTapToZoom = false
    private var restrictBounds = false
    private var animateOnReset = false
    private var autoCenter = false
    private var doubleTapToZoomScaleFactor = 0f

    @AutoResetMode
    private var autoResetMode = 0

    private val last = PointF(0f, 0f)
    private var startScale = 1f
    private var scaleBy = 1f
    private var currentScaleFactor = 1f
    private var previousPointerCount = 1
    private var currentPointerCount = 0

    private var scaleDetector: ScaleGestureDetector? = null
    private var resetAnimator: ValueAnimator? = null

    private var gestureDetector: GestureDetector? = null
    private var doubleTapDetected = false
    private var singleTapDetected = false

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        scaleDetector = ScaleGestureDetector(context, this)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)
        startScaleType = scaleType

        val values = context.obtainStyledAttributes(attrs, R.styleable.ZoomageView)

        zoomable = values.getBoolean(R.styleable.ZoomageView_zoomage_zoomable, true)
        translatable = values.getBoolean(R.styleable.ZoomageView_zoomage_translatable, true)
        animateOnReset = values.getBoolean(R.styleable.ZoomageView_zoomage_animateOnReset, true)
        autoCenter = values.getBoolean(R.styleable.ZoomageView_zoomage_autoCenter, true)
        restrictBounds = values.getBoolean(R.styleable.ZoomageView_zoomage_restrictBounds, false)
        doubleTapToZoom = values.getBoolean(R.styleable.ZoomageView_zoomage_doubleTapToZoom, true)
        minScale = values.getFloat(R.styleable.ZoomageView_zoomage_minScale, MIN_SCALE)
        maxScale = values.getFloat(R.styleable.ZoomageView_zoomage_maxScale, MAX_SCALE)
        doubleTapToZoomScaleFactor =
            values.getFloat(R.styleable.ZoomageView_zoomage_doubleTapToZoomScaleFactor, 3f)
        autoResetMode = fromInt(
            values.getInt(
                R.styleable.ZoomageView_zoomage_autoResetMode,
                AutoResetMode.UNDER
            )
        )

        verifyScaleRange()

        values.recycle()
    }

    private fun verifyScaleRange() {
        check(!(minScale >= maxScale)) { "minScale must be less than maxScale" }

        check(!(minScale < 0)) { "minScale must be greater than 0" }

        check(!(maxScale < 0)) { "maxScale must be greater than 0" }

        if (doubleTapToZoomScaleFactor > maxScale) {
            doubleTapToZoomScaleFactor = maxScale
        }

        if (doubleTapToZoomScaleFactor < minScale) {
            doubleTapToZoomScaleFactor = minScale
        }
    }

    /**
     * Set the minimum and maximum allowed scale for zooming. `minScale` cannot
     * be greater than `maxScale` and neither can be 0 or less. This will result
     * in an [IllegalStateException].
     *
     * @param minScale minimum allowed scale
     * @param maxScale maximum allowed scale
     */
    fun setScaleRange(minScale: Float, maxScale: Float) {
        this.minScale = minScale
        this.maxScale = maxScale

        startValues = null

        verifyScaleRange()
    }

    /**
     * Returns whether the image is translatable.
     *
     * @return true if translation of image is allowed, false otherwise
     */
    fun isTranslatable(): Boolean {
        return translatable
    }

    /**
     * Set the image's translatable state.
     *
     * @param translatable true to enable translation, false to disable it
     */
    fun setTranslatable(translatable: Boolean) {
        this.translatable = translatable
    }

    /**
     * Returns the zoomable state of the image.
     *
     * @return true if pinch-zooming of the image is allowed, false otherwise.
     */
    fun isZoomable(): Boolean {
        return zoomable
    }

    /**
     * Set the zoomable state of the image.
     *
     * @param zoomable true to enable pinch-zooming of the image, false to disable it
     */
    fun setZoomable(zoomable: Boolean) {
        this.zoomable = zoomable
    }

    /**
     * If restricted bounds are enabled, the image will not be allowed to translate
     * farther inward than the edges of the view's bounds, unless the corresponding
     * dimension (width or height) is smaller than those of the view's frame.
     *
     * @return true if image bounds are restricted to the view's edges, false otherwise
     */
    fun getRestrictBounds(): Boolean {
        return restrictBounds
    }

    /**
     * Set the restrictBounds status of the image.
     * If restricted bounds are enabled, the image will not be allowed to translate
     * farther inward than the edges of the view's bounds, unless the corresponding
     * dimension (width or height) is smaller than those of the view's frame.
     *
     * @param restrictBounds true if image bounds should be restricted to the view's edges, false otherwise
     */
    fun setRestrictBounds(restrictBounds: Boolean) {
        this.restrictBounds = restrictBounds
    }

    /**
     * Returns status of animateOnReset. This causes the image to smoothly animate back
     * to its start position when reset. Default value is true.
     *
     * @return true if animateOnReset is enabled, false otherwise
     */
    fun getAnimateOnReset(): Boolean {
        return animateOnReset
    }

    /**
     * Set whether or not the image should animate when resetting.
     *
     * @param animateOnReset true if image should animate when resetting, false to snap
     */
    fun setAnimateOnReset(animateOnReset: Boolean) {
        this.animateOnReset = animateOnReset
    }

    /**
     * Get the current [AutoResetMode] mode of the image. Default value is [AutoResetMode.UNDER].
     *
     * @return the current [AutoResetMode] mode, one of [OVER][AutoResetMode.OVER], [UNDER][AutoResetMode.UNDER],
     * [ALWAYS][AutoResetMode.ALWAYS], or [NEVER][AutoResetMode.NEVER]
     */
    @AutoResetMode
    fun getAutoResetMode(): Int {
        return autoResetMode
    }

    /**
     * Set the [AutoResetMode] mode for the image.
     *
     * @param autoReset the desired mode, one of [OVER][AutoResetMode.OVER], [UNDER][AutoResetMode.UNDER],
     * [ALWAYS][AutoResetMode.ALWAYS], or [NEVER][AutoResetMode.NEVER]
     */
    fun setAutoResetMode(@AutoResetMode autoReset: Int) {
        this.autoResetMode = autoReset
    }

    /**
     * Whether or not the image should automatically center itself when it's dragged partially or
     * fully out of view.
     *
     * @return true if image should center itself automatically, false if it should not
     */
    fun getAutoCenter(): Boolean {
        return autoCenter
    }

    /**
     * Set whether or not the image should automatically center itself when it's dragged
     * partially or fully out of view.
     *
     * @param autoCenter true if image should center itself automatically, false if it should not
     */
    fun setAutoCenter(autoCenter: Boolean) {
        this.autoCenter = autoCenter
    }

    /**
     * Gets double tap to zoom state.
     *
     * @return whether double tap to zoom is enabled
     */
    fun getDoubleTapToZoom(): Boolean {
        return doubleTapToZoom
    }

    /**
     * Sets double tap to zoom state.
     *
     * @param doubleTapToZoom true if double tap to zoom should be enabled
     */
    fun setDoubleTapToZoom(doubleTapToZoom: Boolean) {
        this.doubleTapToZoom = doubleTapToZoom
    }

    /**
     * Gets the double tap to zoom scale factor.
     *
     * @return double tap to zoom scale factor
     */
    fun getDoubleTapToZoomScaleFactor(): Float {
        return doubleTapToZoomScaleFactor
    }

    /**
     * Sets the double tap to zoom scale factor. Can be a maximum of max scale.
     *
     * @param doubleTapToZoomScaleFactor the scale factor you want to zoom to when double tap occurs
     */
    fun setDoubleTapToZoomScaleFactor(doubleTapToZoomScaleFactor: Float) {
        this.doubleTapToZoomScaleFactor = doubleTapToZoomScaleFactor
        verifyScaleRange()
    }

    /**
     * Get the current scale factor of the image, in relation to its starting size.
     *
     * @return the current scale factor
     */
    fun getCurrentScaleFactor(): Float {
        return currentScaleFactor
    }

    /**
     * {@inheritDoc}
     */
    override fun setScaleType(scaleType: ScaleType?) {
        if (scaleType != null) {
            super.setScaleType(scaleType)
            startScaleType = scaleType
            startValues = null
        }
    }

    /**
     * Set enabled state of the view. Note that this will reset the image's
     * [ScaleType] to its pre-zoom state.
     *
     * @param enabled enabled state
     */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        if (!enabled) {
            setScaleType(startScaleType)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        setScaleType(startScaleType)
    }

    /**
     * {@inheritDoc}
     */
    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        setScaleType(startScaleType)
    }

    /**
     * {@inheritDoc}
     */
    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        setScaleType(startScaleType)
    }

    /**
     * {@inheritDoc}
     */
    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        setScaleType(startScaleType)
    }

    /**
     * Update the bounds of the displayed image based on the current matrix.
     *
     * @param values the image's current matrix values.
     */
    private fun updateBounds(values: FloatArray) {
        if (getDrawable() != null) {
            bounds.set(
                values[Matrix.MTRANS_X],
                values[Matrix.MTRANS_Y],
                getDrawable().intrinsicWidth * values[Matrix.MSCALE_X] + values[Matrix.MTRANS_X],
                getDrawable().intrinsicHeight * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]
            )
        }
    }

    /**
     * Get the width of the displayed image.
     *
     * @return the current width of the image as displayed (not the width of the [ImageView] itself.
     */
    private fun getCurrentDisplayedWidth(): Float {
        if (getDrawable() != null) return getDrawable().intrinsicWidth * matrixValues[Matrix.MSCALE_X]
        else return 0f
    }

    /**
     * Get the height of the displayed image.
     *
     * @return the current height of the image as displayed (not the height of the [ImageView] itself.
     */
    private fun getCurrentDisplayedHeight(): Float {
        if (getDrawable() != null) return getDrawable().intrinsicHeight * matrixValues[Matrix.MSCALE_Y]
        else return 0f
    }

    /**
     * Remember our starting values so we can animate our image back to its original position.
     */
    private fun setStartValues() {
        startValues = FloatArray(9)
        startMatrix = Matrix(getImageMatrix())
        startMatrix.getValues(startValues)
        calculatedMinScale = minScale * startValues!![Matrix.MSCALE_X]
        calculatedMaxScale = maxScale * startValues!![Matrix.MSCALE_X]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable && isEnabled && (zoomable || translatable)) {
            if (scaleType != ScaleType.MATRIX) {
                super.setScaleType(ScaleType.MATRIX)
            }

            if (startValues == null) {
                setStartValues()
            }

            currentPointerCount = event.pointerCount

            //get the current state of the image matrix, its values, and the bounds of the drawn bitmap
            matrix.set(getImageMatrix())
            matrix.getValues(matrixValues)
            updateBounds(matrixValues)

            scaleDetector!!.onTouchEvent(event)
            gestureDetector!!.onTouchEvent(event)

            if (doubleTapToZoom && doubleTapDetected) {
                doubleTapDetected = false
                singleTapDetected = false
                if (matrixValues[Matrix.MSCALE_X] != startValues!![Matrix.MSCALE_X]) {
                    reset()
                } else {
                    val zoomMatrix = Matrix(matrix)
                    zoomMatrix.postScale(
                        doubleTapToZoomScaleFactor,
                        doubleTapToZoomScaleFactor,
                        scaleDetector!!.focusX,
                        scaleDetector!!.focusY
                    )
                    animateScaleAndTranslationToMatrix(zoomMatrix, RESET_DURATION)
                }
                return true
            } else if (!singleTapDetected) {
                /* if the event is a down touch, or if the number of touch points changed,
                 * we should reset our start point, as event origins have likely shifted to a
                 * different part of the screen*/
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    currentPointerCount != previousPointerCount
                ) {
                    last.set(scaleDetector!!.focusX, scaleDetector!!.focusY)
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val focusx = scaleDetector!!.focusX
                    val focusy = scaleDetector!!.focusY

                    if (allowTranslate(event)) {
                        //calculate the distance for translation
                        val xdistance = getXDistance(focusx, last.x)
                        val ydistance = getYDistance(focusy, last.y)
                        matrix.postTranslate(xdistance, ydistance)
                    }

                    if (allowZoom(event)) {
                        matrix.postScale(scaleBy, scaleBy, focusx, focusy)
                        currentScaleFactor =
                            matrixValues[Matrix.MSCALE_X] / startValues!![Matrix.MSCALE_X]
                    }

                    setImageMatrix(matrix)

                    last.set(focusx, focusy)
                }

                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    scaleBy = 1f
                    resetImage()
                }
            }

            parent.requestDisallowInterceptTouchEvent(disallowParentTouch(event))

            //this tracks whether they have changed the number of fingers down
            previousPointerCount = currentPointerCount


            return true
        }

        return super.onTouchEvent(event)
    }

    protected fun disallowParentTouch(event: MotionEvent?): Boolean {
        return (currentPointerCount > 1 || currentScaleFactor > 1.0f || isAnimating())
    }

    protected fun allowTranslate(event: MotionEvent?): Boolean {
        return translatable && currentScaleFactor > 1.0f
    }

    protected fun allowZoom(event: MotionEvent?): Boolean {
        return zoomable
    }

    private fun isAnimating(): Boolean {
        return resetAnimator != null && resetAnimator!!.isRunning
    }

    /**
     * Reset the image based on the specified [AutoResetMode] mode.
     */
    private fun resetImage() {
        when (autoResetMode) {
            AutoResetMode.UNDER -> if (matrixValues[Matrix.MSCALE_X] <= startValues!![Matrix.MSCALE_X]) {
                reset()
            } else {
                center()
            }

            AutoResetMode.OVER -> if (matrixValues[Matrix.MSCALE_X] >= startValues!![Matrix.MSCALE_X]) {
                reset()
            } else {
                center()
            }

            AutoResetMode.ALWAYS -> reset()
            AutoResetMode.NEVER -> center()
        }
    }

    /**
     * This helps to keep the image on-screen by animating the translation to the nearest
     * edge, both vertically and horizontally.
     */
    private fun center() {
        if (autoCenter) {
            animateTranslationX()
            animateTranslationY()
        }
    }

    /**
     * Reset image back to its starting size. If `animate` is false, image
     * will snap back to its original size.
     *
     * @param animate animate the image back to its starting size
     */
    /**
     * Reset image back to its original size. Will snap back to original size
     * if animation on reset is disabled via [.setAnimateOnReset].
     */
    @JvmOverloads
    fun reset(animate: Boolean = animateOnReset) {
        if (animate) {
            animateToStartMatrix()
        } else {
            setImageMatrix(startMatrix)
        }
    }

    /**
     * Animate the matrix back to its original position after the user stopped interacting with it.
     */
    private fun animateToStartMatrix() {
        animateScaleAndTranslationToMatrix(startMatrix, RESET_DURATION)
    }

    /**
     * Animate the scale and translation of the current matrix to the target
     * matrix.
     *
     * @param targetMatrix the target matrix to animate values to
     */
    private fun animateScaleAndTranslationToMatrix(targetMatrix: Matrix, duration: Int) {
        val targetValues = FloatArray(9)
        targetMatrix.getValues(targetValues)

        val beginMatrix = Matrix(getImageMatrix())
        beginMatrix.getValues(matrixValues)

        //difference in current and original values
        val xsdiff = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val ysdiff = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val xtdiff = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val ytdiff = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]

        resetAnimator = ValueAnimator.ofFloat(0f, 1f)
        resetAnimator?.addUpdateListener(object : AnimatorUpdateListener {
            val activeMatrix: Matrix = Matrix(getImageMatrix())
            val values: FloatArray = FloatArray(9)

            override fun onAnimationUpdate(animation: ValueAnimator) {
                val `val` = animation.getAnimatedValue() as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xtdiff * `val`
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + ytdiff * `val`
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xsdiff * `val`
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + ysdiff * `val`
                activeMatrix.setValues(values)
                setImageMatrix(activeMatrix)
            }
        })

        resetAnimator?.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                setImageMatrix(targetMatrix)
            }
        })

        resetAnimator?.setDuration(duration.toLong())
        resetAnimator?.start()
    }

    private fun animateTranslationX() {
        if (getCurrentDisplayedWidth() > width) {
            //the left edge is too far to the interior
            if (bounds.left > 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            } else if (bounds.right < width) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + width - bounds.right)
            }
        } else {
            //left edge needs to be pulled in, and should be considered before the right edge
            if (bounds.left < 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            } else if (bounds.right > width) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + width - bounds.right)
            }
        }
    }

    private fun animateTranslationY() {
        if (getCurrentDisplayedHeight() > height) {
            //the top edge is too far to the interior
            if (bounds.top > 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            } else if (bounds.bottom < height) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + height - bounds.bottom)
            }
        } else {
            //top needs to be pulled in, and needs to be considered before the bottom edge
            if (bounds.top < 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            } else if (bounds.bottom > height) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + height - bounds.bottom)
            }
        }
    }

    private fun animateMatrixIndex(index: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[index], to)
        animator.addUpdateListener(object : AnimatorUpdateListener {
            val values: FloatArray = FloatArray(9)
            var current: Matrix = Matrix()

            override fun onAnimationUpdate(animation: ValueAnimator) {
                current.set(getImageMatrix())
                current.getValues(values)
                values[index] = (animation.getAnimatedValue() as Float?)!!
                current.setValues(values)
                setImageMatrix(current)
            }
        })
        animator.setDuration(RESET_DURATION.toLong())
        animator.start()
    }

    /**
     * Get the x distance to translate the current image.
     *
     * @param toX   the current x location of touch focus
     * @param fromX the last x location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getXDistance(toX: Float, fromX: Float): Float {
        var xDistance = toX - fromX

        if (restrictBounds) {
            xDistance = getRestrictedXDistance(xDistance)
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.right + xDistance < 0) {
            xDistance = -bounds.right
        } else if (bounds.left + xDistance > width) {
            xDistance = width - bounds.left
        }

        return xDistance
    }

    /**
     * Get the horizontal distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param xDistance the current desired horizontal distance to translate
     * @return the actual horizontal distance to translate with bounds restrictions
     */
    private fun getRestrictedXDistance(xDistance: Float): Float {
        var restrictedXDistance = xDistance

        if (getCurrentDisplayedWidth() >= width) {
            if (bounds.left <= 0 && bounds.left + xDistance > 0 && !scaleDetector!!.isInProgress) {
                restrictedXDistance = -bounds.left
            } else if (bounds.right >= width && bounds.right + xDistance < width && !scaleDetector!!.isInProgress) {
                restrictedXDistance = width - bounds.right
            }
        } else if (!scaleDetector!!.isInProgress) {
            if (bounds.left >= 0 && bounds.left + xDistance < 0) {
                restrictedXDistance = -bounds.left
            } else if (bounds.right <= width && bounds.right + xDistance > width) {
                restrictedXDistance = width - bounds.right
            }
        }

        return restrictedXDistance
    }

    /**
     * Get the y distance to translate the current image.
     *
     * @param toY   the current y location of touch focus
     * @param fromY the last y location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getYDistance(toY: Float, fromY: Float): Float {
        var yDistance = toY - fromY

        if (restrictBounds) {
            yDistance = getRestrictedYDistance(yDistance)
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.bottom + yDistance < 0) {
            yDistance = -bounds.bottom
        } else if (bounds.top + yDistance > height) {
            yDistance = height - bounds.top
        }

        return yDistance
    }

    /**
     * Get the vertical distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param yDistance the current desired vertical distance to translate
     * @return the actual vertical distance to translate with bounds restrictions
     */
    private fun getRestrictedYDistance(yDistance: Float): Float {
        var restrictedYDistance = yDistance

        if (getCurrentDisplayedHeight() >= height) {
            if (bounds.top <= 0 && bounds.top + yDistance > 0 && !scaleDetector!!.isInProgress) {
                restrictedYDistance = -bounds.top
            } else if (bounds.bottom >= height && bounds.bottom + yDistance < height && !scaleDetector!!.isInProgress) {
                restrictedYDistance = height - bounds.bottom
            }
        } else if (!scaleDetector!!.isInProgress) {
            if (bounds.top >= 0 && bounds.top + yDistance < 0) {
                restrictedYDistance = -bounds.top
            } else if (bounds.bottom <= height && bounds.bottom + yDistance > height) {
                restrictedYDistance = height - bounds.bottom
            }
        }

        return restrictedYDistance
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        //calculate value we should scale by, ultimately the scale will be startScale*scaleFactor

        scaleBy = (startScale * detector.getScaleFactor()) / matrixValues[Matrix.MSCALE_X]

        //what the scaling should end up at after the transformation
        val projectedScale = scaleBy * matrixValues[Matrix.MSCALE_X]

        //clamp to the min/max if it's going over
        if (projectedScale < calculatedMinScale) {
            scaleBy = calculatedMinScale / matrixValues[Matrix.MSCALE_X]
        } else if (projectedScale > calculatedMaxScale) {
            scaleBy = calculatedMaxScale / matrixValues[Matrix.MSCALE_X]
        }

        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        startScale = matrixValues[Matrix.MSCALE_X]
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaleBy = 1f
    }

    private val gestureListener: GestureDetector.OnGestureListener =
        object : SimpleOnGestureListener() {
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_UP) {
                    doubleTapDetected = true
                }

                singleTapDetected = false

                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                singleTapDetected = true
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                singleTapDetected = false
                return false
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        }

    private open inner class SimpleAnimatorListener : AnimatorListener {
        override fun onAnimationStart(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
        }

        override fun onAnimationCancel(animation: Animator) {
        }

        override fun onAnimationRepeat(animation: Animator) {
        }
    }

    companion object {
        private const val MIN_SCALE = 0.6f
        private const val MAX_SCALE = 8f
    }
}
