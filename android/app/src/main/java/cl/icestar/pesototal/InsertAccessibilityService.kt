package cl.icestar.pesototal

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Escribe el total en el campo que tenga el foco de entrada.
 *
 * Apunta al nodo enfocado, nunca a un id del WMS: asi no se rompe cuando el WMS
 * se actualice. Alcance minimo declarado en res/xml/accessibility_service.xml.
 */
class InsertAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: InsertAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** false = no hay servicio, no hay campo enfocado, o el campo lo rechazo. */
        fun setFocusedText(text: String): Boolean {
            val svc = instance ?: return false
            val node: AccessibilityNodeInfo =
                svc.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                if (!node.isEditable) return false
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
    }
}
