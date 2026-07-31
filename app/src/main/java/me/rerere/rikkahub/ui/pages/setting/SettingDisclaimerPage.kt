package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

private val DISCLAIMER_CONTENT = """
## 重要提示

**下载、安装、打开、注册、配置或继续使用本应用（以下简称「本软件」），即视为您已阅读、理解并同意受本免责声明及使用条款（以下简称「本条款」）的全部约束。**

若您不同意本条款的任何内容，请立即停止使用并卸载本软件。您的持续使用行为，构成对本条款的默认接受。

本软件开发者、维护者、贡献者、分发渠道及相关关联方（以下统称「我们」）在法律允许的最大范围内，对本软件的使用不承担任何明示或默示责任。

---

## 一、接受与同意

1. 您确认已具备完全民事行为能力，或已在监护人同意下使用本软件。
2. 您代表本人，或在获得充分授权的情况下代表所在组织接受本条款。
3. 本条款可随时更新；更新后继续使用，视为接受修订后的条款。我们无义务另行逐一通知。

---

## 二、软件性质与定位

1. 本软件主要为 AI 对话及相关辅助功能的客户端工具，**并非** OpenAI、Anthropic、Google 或其他任何模型服务商的官方产品。
2. 本软件可能对接第三方模型 API、搜索、语音、文档解析、本地工具等能力；实际效果取决于您自行配置的服务与网络环境。
3. 我们不保证本软件与任何第三方服务长期兼容，也不保证功能持续可用、无中断或无变更。

---

## 三、按「现状」提供，不提供任何担保

在适用法律允许的最大范围内，本软件按「现状（AS IS）」和「现有（AS AVAILABLE）」提供，**不作任何形式的明示或默示担保**，包括但不限于：

- 适销性、特定用途适用性、所有权、非侵权；
- 准确性、完整性、及时性、可靠性、安全性、无错误；
- 不中断运行、无病毒、无漏洞、满足您的全部期望。

您自行承担因下载、安装、配置、使用、无法使用本软件而产生的全部风险。

---

## 四、第三方服务、API Key 与费用

1. 使用 Claude、ChatGPT、Gemini 等模型，通常需要您自行向官方或第三方中转站申请并填写 API Key、Base URL 等凭证。
2. **API Key、令牌、账号密码等敏感信息由您自行保管。** 因泄露、被盗用、错误配置、中转站跑路、掺水、限流、封禁等导致的损失，均由您自行承担。
3. 模型调用、搜索、语音等可能产生费用；费用由对应服务商向您收取。我们不代收、不垫付，也不对账单争议负责。
4. 第三方服务的内容政策、可用性、定价、地区限制可能随时变化，我们无法控制，亦不承担责任。
5. 请勿将他人的 API Key 或未获授权的凭证写入本软件。

---

## 五、人工智能输出免责

1. AI 生成内容可能不准确、过时、片面、有偏见或具有误导性（包括「幻觉」）。
2. **AI 输出不构成法律、医疗、心理、金融、投资、安全或其他专业建议。** 重要决策请咨询具备资质的专业人士，并独立核实。
3. 您不得将本软件用于诊断疾病、实施治疗、紧急救助、操控危险设备，或任何可能造成人身伤害、财产损失的场景，除非您自行承担全部后果。
4. 因依赖、采纳或传播 AI 输出而产生的任何直接或间接损失，我们概不负责。

---

## 六、用户行为与内容责任

您对通过本软件输入、上传、生成、存储、转发、导出的全部内容负完全责任，并保证：

1. 不违反所在地法律法规、公共秩序与善良风俗；
2. 不侵犯他人知识产权、隐私权、肖像权、商业秘密等合法权益；
3. 不制作、传播违法、淫秽、暴力、歧视、欺诈、恶意软件等内容；
4. 不利用本软件攻击、渗透、干扰任何系统或服务；
5. 不规避安全机制、滥用自动化接口，或以任何方式损害本软件及其他用户权益。

因您的内容或行为引发的投诉、处罚、诉讼、索赔，均由您自行处理并承担全部后果。

---

## 七、数据、隐私与本地存储

1. 对话记录、设置、密钥等可能存储于您的设备本地；设备丢失、系统故障、误删、越狱/Root、恶意软件等导致的数据损失，我们不承担责任。
2. 当您连接第三方 API 时，相关请求内容可能被传输至该服务商服务器，并受其隐私政策约束。请自行阅读并接受第三方条款。
3. 备份、导出、分享、同步等操作由您主动触发时，风险由您承担。
4. 我们无法保证本地或传输过程中的绝对安全，请勿在本软件中处理高度敏感或机密信息。

---

## 八、工具、自动化与设备权限

1. 本软件可能请求或使用相机、麦克风、存储、通知、网络、无障碍、文件访问、本地终端/工作区等权限或能力。
2. 您开启相关权限或工具后，即视为授权本软件在相应范围内运行；由此产生的误操作、数据覆盖、脚本执行后果等，由您自行承担。
3. 工作区、Shell、MCP、插件、扩展等高级能力具有较高风险，请仅在充分理解的前提下使用。

---

## 九、责任限制

在法律允许的最大范围内，我们对以下事项**不承担任何责任**（无论基于合同、侵权、严格责任或其他理论）：

1. 任何间接、附带、特殊、惩罚性、后果性损害，包括利润、商誉、数据、商机损失；
2. 因使用或无法使用本软件导致的人身伤害、精神损害、财产损失；
3. 第三方服务故障、网络中断、运营商限制、政策变化；
4. 未经授权的访问、窃听、篡改、攻击；
5. 软件缺陷、兼容性问题、更新失败、卸载残留；
6. 您或第三方对本软件的修改、逆向、二次分发。

即便我们事先知悉损害可能发生，上述限制仍然适用。若强制法律规定不得完全免责，我们的总责任以您为使用本软件实际向我们支付的金额为上限（如为免费使用，则以零元为上限），或适用法律允许的最低限额。

---

## 十、赔偿

若因您违反本条款、滥用本软件，或因您提供的内容，导致我们对第三方承担赔偿、罚款、律师费或其他支出，您同意赔偿并使我们免受损害。

---

## 十一、开源组件与第三方代码

本软件可能包含开源组件或基于第三方项目修改。相关组件按其各自许可证授权；我们不对第三方组件的质量、安全或许可冲突提供额外保证。使用本软件即表示您理解并接受相关开源许可的约束。

---

## 十二、终止

我们可在不事先通知的情况下，暂停或终止对特定功能的支持。您可随时停止使用并卸载本软件。终止后，本条款中依性质应继续有效的条款（包括免责、责任限制、赔偿等）仍然有效。

---

## 十三、可分割性与完整协议

本条款某一条款被认定无效或不可执行的，不影响其余条款效力。本条款构成您与我们之间就本软件使用的完整约定（另有书面约定除外），并取代此前相关口头或书面陈述。

---

## 十四、适用法律与争议

本条款的解释与适用，在不违反强制性法律规定的前提下，由本软件运营主体所在地法律管辖。争议应优先友好协商；协商不成的，提交有管辖权的人民法院解决。部分地区法律可能赋予您不可放弃的权利，本条款无意剥夺该等权利。

---

## 十五、最终说明

**再次确认：使用本软件即表示您默认同意本免责声明的全部内容。因使用本软件所产生的任何问题、损失、纠纷、法律后果，均与开发者本人及关联方无关，由您自行承担。**

如您仍有疑问，请在继续使用前审慎评估风险。停止使用是您拒绝本条款的唯一有效方式。
""".trimIndent()

@Composable
fun SettingDisclaimerPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("免责声明") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "使用本应用即视为同意以下全部条款",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "请仔细阅读。若不同意，请立即停止使用并卸载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MarkdownBlock(
                    content = DISCLAIMER_CONTENT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                )
            }
        }
    }
}
