package dev.skomlach.biometric.compat.utils.activityView

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.AsyncTask
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object BlurUtil {
    private const val BITMAP_SCALE = 0.4f
    private const val BLUR_RADIUS = 7.5f

    interface OnPublishListener {
        fun onBlurredScreenshot(bm: Bitmap)
    }

    fun takeScreenshotAndBlur(view: View, listener: OnPublishListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decorView: View? = (view.context as Activity).window.peekDecorView()
            decorView?.let {
                try {
                    //workaround for IllegalStateException("Window doesn't have a backing surface!")
                    val method = View::class.java.getMethod("getViewRootImpl")
                    val root = method.invoke(decorView) ?: return
                    val field = try {
                        root.javaClass.getField("mSurface")
                    } catch (ignore: Throwable) {
                        root.javaClass.fields.firstOrNull { it.type.name == Surface::class.java.name }
                    }
                    val surface: Surface? = field?.get(root) as Surface?
                    if (surface?.isValid == false) {
                        return
                    }
                    val bitmap = Bitmap.createBitmap(
                        view.width,
                        view.height,
                        Bitmap.Config.ARGB_8888
                    )

                    val rect = Rect()
                    if(view.getGlobalVisibleRect(rect)) {
                        PixelCopy.request(
                            (view.context as Activity).window,
                            rect,
                            bitmap,
                            { result ->
                                when (result) {
                                    PixelCopy.SUCCESS -> {
                                        blur(view.context, bitmap, listener)
                                    }
                                }
                            },
                            ExecutorHelper.INSTANCE.handler
                        )
                    }
                } catch (e: Throwable) {

                }

            }
        } else {
            //fallback for Pre-Oreo devices
            val isDone = AtomicBoolean(false)
            val takeScreenshot = {
                val decorView: View? = (view.context as Activity).window.peekDecorView()
                decorView?.let {
                    try {
                        val old = view.isDrawingCacheEnabled
                        if (!old) {
                            view.isDrawingCacheEnabled = true
                            view.buildDrawingCache()//WARNING: may produce exceptions in draw()
                        }
                        try {
                            view.drawingCache?.let {
                                val bm = Bitmap.createBitmap(it)
                                isDone.set(true)
                                blur(view.context, bm, listener)
                            }
                        } finally {
                            if (!old) {
                                view.destroyDrawingCache()
                                view.isDrawingCacheEnabled = false
                            }
                        }
                    } catch (ex: Throwable) {
                        isDone.set(true)
                    }
                }
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (view.viewTreeObserver.isAlive) {
                        takeScreenshot.invoke()
                        if (isDone.get()) {
                            view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                }
            })
        }
    }

    fun blur(context: Context, image: Bitmap, listener: OnPublishListener) {
        if (Build.VERSION.SDK_INT >= 17) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                val width = (image.width * BITMAP_SCALE).roundToInt()
                val height = (image.height * BITMAP_SCALE).roundToInt()
                val inputBitmap = Bitmap.createScaledBitmap(image, width, height, false)
                val outputBitmap = Bitmap.createBitmap(inputBitmap)
                val rs = RenderScript.create(context)
                val theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                val tmpIn = Allocation.createFromBitmap(rs, inputBitmap)
                val tmpOut = Allocation.createFromBitmap(rs, outputBitmap)
                theIntrinsic.setRadius(BLUR_RADIUS)
                theIntrinsic.setInput(tmpIn)
                theIntrinsic.forEach(tmpOut)
                tmpOut.copyTo(outputBitmap)
                ExecutorHelper.INSTANCE.handler.post { listener.onBlurredScreenshot(outputBitmap) }
            }
        }
    }
}