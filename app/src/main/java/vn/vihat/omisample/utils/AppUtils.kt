package vn.vihat.omisample.utils

import PrefManager
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlin.math.abs

object AppUtils {

    fun toggleKeyboard(context: Context, isShow: Boolean = false, view: View? = null, windowToken: IBinder? = null) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isShow) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    fun keyboardDismiss(
        activity: Activity,
        event: MotionEvent?,
        lastPressDownX: Int,
        onPressDown: (downX: Int) -> Unit
    ) {
        when (event!!.action) {
            MotionEvent.ACTION_DOWN -> onPressDown(event.rawX.toInt())
            MotionEvent.ACTION_UP -> {
                val v: View? = activity.currentFocus
                if (v is EditText) {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()
                    // Was it a scroll - If skip all
                    if (abs(lastPressDownX - x) > 5) return
                    val reducePx = 25
                    val outRect = Rect()
                    v.getGlobalVisibleRect(outRect)
                    // Bounding box is to big, reduce it just a little bit
                    outRect.inset(reducePx, reducePx)
                    if (!outRect.contains(x, y)) {
                        v.clearFocus()
                        var touchTargetIsEditText = false
                        // Check if another editText has been touched
                        for (vi in v.getRootView().touchables) {
                            if (vi is EditText) {
                                val clickedViewRect = Rect()
                                vi.getGlobalVisibleRect(clickedViewRect)
                                // Bounding box is to big, reduce it just a little bit
                                clickedViewRect.inset(reducePx, reducePx)
                                if (clickedViewRect.contains(x, y)) {
                                    touchTargetIsEditText = true
                                    break
                                }
                            }
                        }
                        if (!touchTargetIsEditText) toggleKeyboard(activity, windowToken = v.windowToken)
                    }
                }
            }
        }
    }

    /**
     * This is a function simulating session management. When integrating, please use the session management mechanism of your app.
     */
    fun setSession(context: Context, isLogin: Boolean) {
        val editor = PrefManager(context).editor()
        editor.putBoolean("SESSION", isLogin)
        editor.apply()
    }


    /**
     * This is a function simulating session management. When integrating, please use the session management mechanism of your app.
     */
    fun checkSession(context: Context): Boolean {
        val pref = PrefManager(context).pref()
        return pref.getBoolean("SESSION", false)
    }

}