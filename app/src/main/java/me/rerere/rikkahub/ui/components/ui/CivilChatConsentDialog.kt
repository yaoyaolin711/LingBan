package me.rerere.rikkahub.ui.components.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private data class ConsentClause(
    val title: String,
    val body: String,
)

private val CIVIL_CHAT_CLAUSES = listOf(
    ConsentClause(
        title = "一、文明上网倡议",
        body = "欢迎使用本应用。我们倡导清朗网络空间与绿色聊天：请理性表达、友善交流，共同维护健康的线上氛围。",
    ),
    ConsentClause(
        title = "二、遵守法律法规",
        body = "请勿利用本应用制作、传播或获取违法违规信息，包括但不限于危害国家安全、破坏社会稳定、宣扬暴力恐怖、涉赌涉毒等内容。",
    ),
    ConsentClause(
        title = "三、绿色聊天与友善表达",
        body = "请尊重他人，避免人身攻击、歧视、侮辱、骚扰或恶意引战。对话中请尽量使用礼貌、建设性的表达方式。",
    ),
    ConsentClause(
        title = "四、内容真实性与责任",
        body = "人工智能生成内容可能存在错误或不完整。请勿将 AI 输出视为专业建议；重要信息请自行核实。您对自身输入与传播的内容负完全责任。",
    ),
    ConsentClause(
        title = "五、保护隐私与他人权益",
        body = "请勿擅自上传、泄露他人隐私信息，或侵犯他人肖像权、著作权等合法权益。未经授权请勿分享敏感资料。",
    ),
    ConsentClause(
        title = "六、未成年人保护",
        body = "未成年人请在监护人指导下使用。请勿向未成年人推送或诱导接触不适宜内容。",
    ),
    ConsentClause(
        title = "七、安全使用提示",
        body = "请妥善保管账号、密钥与本地数据。不要轻信陌生链接或要求转账、验证码等敏感操作的对话内容。",
    ),
    ConsentClause(
        title = "八、同意与生效",
        body = "请完整阅读本提示后点击「我同意」。同意即表示您已理解并承诺遵守上述文明上网与绿色聊天要求。若不同意，请停止使用并退出应用。",
    ),
)

/**
 * 首次进入强制阅读的文明上网提示。
 * 须滚动到条款末尾后，「我同意」才可点击；不可点外部或返回关闭。
 */
@Composable
fun CivilChatConsentDialog(
    onAgree: () -> Unit,
) {
    BackHandler(enabled = true) {
        // 强制阅读期间拦截返回
    }

    val scrollState = rememberScrollState()
    // 首帧 layout 前 maxValue=0 且 canScrollForward=false，会误判「已读完」
    var viewportReady by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState) {
        // 等到发生过至少一次测量回调（maxValue 变化或短暂等待后仍为 0）
        val firstMax = snapshotFlow { scrollState.maxValue }
            .distinctUntilChanged()
            .first()
        if (firstMax == 0) delay(64)
        viewportReady = true
    }
    val reachedEnd by remember {
        derivedStateOf {
            viewportReady && !scrollState.canScrollForward
        }
    }

    AlertDialog(
        onDismissRequest = { /* 强制同意，不允许关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = true,
        ),
        title = {
            Text(
                text = "文明上网提示",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "为共建清朗网络环境，请在开始使用前阅读并同意以下内容。请滑动至底部完成阅读。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CIVIL_CHAT_CLAUSES.forEach { clause ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = clause.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = clause.body,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (reachedEnd) {
                        "已阅读完毕，可点击下方按钮继续。"
                    } else {
                        "请继续向下滑动，阅读完全部条款。"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reachedEnd) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                enabled = reachedEnd,
            ) {
                Text(if (reachedEnd) "我同意" else "请先读完")
            }
        },
    )
}
