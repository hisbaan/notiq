package com.hisbaan.notiq.fill

import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import com.hisbaan.notiq.core.FieldInfo
import com.hisbaan.notiq.core.ScreenInfo

/** Accessibility nodes → the plain descriptions CodeFieldDetector works on. */
object FieldReader {
    fun field(node: AccessibilityNodeInfo): FieldInfo {
        val inputClass = node.inputType and InputType.TYPE_MASK_CLASS
        return FieldInfo(
            hint = node.hintText?.toString(),
            viewId = node.viewIdResourceName,
            label = label(node),
            contentDescription = node.contentDescription?.toString(),
            text = node.text?.toString()?.takeUnless { node.isShowingHintText },
            // Phone keyboards are digit pads too; OTP fields use them.
            numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE,
            password = node.isPassword,
            maxLength = node.maxTextLength.takeIf { it > 0 },
        )
    }

    /**
     * Editable fields next to [node], left to right, [node] included: the boxes of a split code
     * field. Each box is often wrapped in its own container, so this looks under the grandparent
     * when the parent holds only one field.
     */
    fun siblings(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val parent = node.parent ?: return listOf(node)
        val near = editables(parent, depth = 1)
        val boxes = if (near.size > 1) near else node.parent?.parent?.let { editables(it, depth = 2) } ?: near
        return boxes.ifEmpty { listOf(node) }.sortedBy { bounds(it).left }
    }

    /** Visible text and the number of editable fields, from the window's root. */
    fun screen(root: AccessibilityNodeInfo?): ScreenInfo {
        root ?: return ScreenInfo()
        val text = StringBuilder()
        var editable = 0
        var visited = 0
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (!node.isVisibleToUser) continue
            if (node.isEditable) {
                editable++
            } else if (text.length < MAX_TEXT) {
                node.text?.let { text.append(it).append('\n') }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return ScreenInfo(text.toString(), editable.coerceAtLeast(1))
    }

    /** Android 16 allows several labels per node and deprecates the single one. */
    private fun label(node: AccessibilityNodeInfo): String? {
        val labels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            node.labeledByList
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(node.labeledBy)
        }
        return labels.mapNotNull { it.text?.toString() }.joinToString(" ").ifBlank { null }
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private fun editables(node: AccessibilityNodeInfo, depth: Int): List<AccessibilityNodeInfo> =
        (0 until node.childCount).flatMap { i ->
            val child = node.getChild(i) ?: return@flatMap emptyList()
            when {
                child.isEditable && child.isVisibleToUser -> listOf(child)
                depth > 0 -> editables(child, depth - 1)
                else -> emptyList()
            }
        }

    private const val MAX_NODES = 400
    private const val MAX_TEXT = 2_000
}
