package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.accessibility.UiBounds
import me.rerere.rikkahub.data.accessibility.UiTreeNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultActionVerifierTest {

    private val verifier = DefaultActionVerifier()

    private fun snap(
        page: String,
        pkg: String = "com.example",
        text: String = "",
        editable: Boolean = false,
        className: String = "android.widget.TextView",
    ) = UISnapshot(
        page = page,
        packageName = pkg,
        timestamp = 1L,
        root = UiTreeNode(
            nodeId = "n0",
            text = text,
            className = className,
            editable = editable,
            focused = editable,
            bounds = UiBounds(0, 0, 10, 10),
        ),
        nodeCount = 1,
    )

    private fun ctx(
        action: AgentAction,
        before: UISnapshot,
        after: UISnapshot,
        executeOk: Boolean = true,
        attempt: Int = 1,
        maxRetries: Int = 2,
    ) = VerifyContext(
        goal = "test",
        action = action,
        executeResult = ActionExecuteResult(executeOk, if (executeOk) "ok" else "fail"),
        before = before,
        after = after,
        attempt = attempt,
        maxRetries = maxRetries,
    )

    @Test
    fun click_pageChange_success() = runBlocking {
        val result = verifier.verify(
            ctx(
                action = AgentAction(
                    AgentAction.CLICK_NODE,
                    target = "发送",
                    params = mapOf("expect_page_change" to "true"),
                ),
                before = snap("MainActivity"),
                after = snap("ChatActivity", text = "已发送"),
            )
        )
        assertEquals(VerificationStatus.SUCCESS, result.status)
        assertTrue(result.checks.any { it.name == "page_changed" && it.passed })
    }

    @Test
    fun click_expectMessage_retryWhenMissing() = runBlocking {
        val result = verifier.verify(
            ctx(
                action = AgentAction(
                    AgentAction.CLICK_NODE,
                    params = mapOf("expect_message" to "张三"),
                ),
                before = snap("Chat"),
                after = snap("Chat", text = "空会话"),
                attempt = 1,
            )
        )
        assertEquals(VerificationStatus.RETRY, result.status)
    }

    @Test
    fun input_editTextMatch() = runBlocking {
        val result = verifier.verify(
            ctx(
                action = AgentAction(AgentAction.TYPE_TEXT, target = "你好"),
                before = snap("Chat", text = "", editable = true, className = "android.widget.EditText"),
                after = snap("Chat", text = "你好", editable = true, className = "android.widget.EditText"),
            )
        )
        assertEquals(VerificationStatus.SUCCESS, result.status)
        assertTrue(result.checks.any { it.name == "edit_text_match" && it.passed })
    }

    @Test
    fun page_packageAndActivity() = runBlocking {
        val result = verifier.verify(
            ctx(
                action = AgentAction(
                    AgentAction.OPEN_APP,
                    target = "com.example",
                    params = mapOf(
                        "expect_package" to "com.example",
                        "expect_activity" to "MainActivity",
                    ),
                ),
                before = snap(""),
                after = snap("com.example.MainActivity", pkg = "com.example"),
            )
        )
        assertEquals(VerificationStatus.SUCCESS, result.status)
    }

    @Test
    fun loadingGone_failsWhenProgressVisible() = runBlocking {
        val after = UISnapshot(
            page = "Chat",
            packageName = "com.example",
            timestamp = 1L,
            root = UiTreeNode(
                nodeId = "n0",
                text = "加载中",
                className = "android.widget.ProgressBar",
                bounds = UiBounds(0, 0, 10, 10),
            ),
            nodeCount = 1,
        )
        val result = verifier.verify(
            ctx(
                action = AgentAction(
                    AgentAction.CLICK_NODE,
                    params = mapOf(
                        "expect_page_change" to "false",
                        "expect_loading_gone" to "true",
                    ),
                ),
                before = snap("Chat"),
                after = after,
                attempt = 3,
                maxRetries = 2,
            )
        )
        assertEquals(VerificationStatus.FAILED, result.status)
        assertFalse(result.checks.first { it.name == "loading_gone" }.passed)
    }
}
