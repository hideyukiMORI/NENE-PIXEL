package io.github.hideyukimori.nenepixel.acceptance

import android.content.ComponentName
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/** Drives the real system picker. No broker completion or production test hook is used. */
internal class AcceptanceDocumentsUi(
    private val awaitCondition: (() -> Boolean) -> Unit,
) {
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val pickerPackage: String by lazy {
        val command =
            "cmd package resolve-activity --brief -a android.intent.action.CREATE_DOCUMENT " +
                "-c android.intent.category.OPENABLE -t image/png"
        val output =
            ParcelFileDescriptor
                .AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader()
                .use { it.readText() }
        checkNotNull(ComponentName.unflattenFromString(output.lineSequence().last { '/' in it }.trim())).packageName
    }

    fun create(name: String) {
        selectRoot()
        val arguments =
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    name,
                )
            }
        check(
            awaitNode { it.viewIdResourceName == "android:id/title" && it.className == "android.widget.EditText" }
                .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments),
        )
        click(awaitNode { it.viewIdResourceName == "android:id/button1" && it.isEnabled })
    }

    fun open(name: String) {
        selectRoot()
        click(awaitNode { it.text?.toString() == name })
    }

    private fun selectRoot() {
        val toolbar = awaitNode { it.viewIdResourceName == "$pickerPackage:id/toolbar" }
        val button = descendants(toolbar).first { it.className == "android.widget.ImageButton" && it.isClickable }
        click(button)
        click(
            awaitNode {
                it.text?.toString() == AcceptanceDocumentsProvider.ROOT_TITLE &&
                    it.viewIdResourceName == "android:id/title"
            },
        )
        awaitNode {
            it.viewIdResourceName == "$pickerPackage:id/toolbar" &&
                descendants(it).any { child -> child.text?.toString() == AcceptanceDocumentsProvider.ROOT_TITLE }
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        awaitCondition {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == pickerPackage) {
                result = descendants(root).firstOrNull(predicate)
            }
            result != null
        }
        return checkNotNull(result)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        check(checkNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun descendants(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> =
        sequence {
            yield(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let { yieldAll(descendants(it)) }
        }
}
