package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.utils.plus

/**
 * 亲密互动说明页。
 * 用于向用户解释该功能的预期方向、边界和当前状态。
 */
@Composable
fun SettingIntimatePage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("亲密互动") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp) + innerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CardGroup {
                    item(
                        headlineContent = { Text("这是什么功能？") },
                        supportingContent = {
                            Text(
                                "“亲密互动”是一个面向 AI 伴侣场景的扩展方向。" +
                                    "它关注的不是普通问答，而是更有陪伴感、氛围感和关系感的互动体验。" +
                                    "这可能包括更贴近关系状态的表达、带节奏的互动流程，或者未来与特定外设联动的沉浸式体验。",
                            )
                        },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("当前状态：暂缓开发") },
                        supportingContent = {
                            Text(
                                "这块功能并不是废弃，而是因为我们目前对真实需求、设备兼容范围、交互边界和安全策略掌握得还不够，所以先暂停正式开发。" +
                                    "在方案不清晰前，这里不会上线实际控制能力，也不会默认接入高风险路径。",
                            )
                        },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("未来可能包含什么？") },
                        supportingContent = {
                            Text(
                                "可能的方向包括：\n" +
                                    "· 纯文案 / 剧情向的亲密互动\n" +
                                    "· 根据关系状态、情绪和上下文触发不同互动\n" +
                                    "· 与特定品牌或协议兼容的外设联动\n" +
                                    "· 更细致的强度、节奏、时长和安全边界控制\n\n" +
                                    "最终会做哪些能力，取决于需求是否明确、实现是否可靠，以及是否能把安全边界定义清楚。",
                            )
                        },
                    )
                }
            }

            item {
                CardGroup {
                    item(
                        headlineContent = { Text("我们需要你提供什么？") },
                        supportingContent = {
                            Text(
                                "欢迎告诉你更看重什么，例如：\n" +
                                    "· 想控哪些品牌 / 型号的设备\n" +
                                    "· 更希望走 App 直连、品牌 App，还是别的方式\n" +
                                    "· 伴侣情绪驱动时，你期望的互动节奏与安全边界\n" +
                                    "· 不需要硬件、只想要剧情 / 文案向的亲密互动\n\n" +
                                    "有想法可通过反馈渠道或社区告诉我们。没有足够建议前，这里不会上线实际控制能力。",
                            )
                        },
                    )
                }
            }

            item {
                Text(
                    text = "说明：原先基于 Intiface / Buttplug 的试验方案已移除，不再作为当前实现路径。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}
