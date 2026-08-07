# L2 记忆模块优化规划（无语义检索路线）

> 状态：规划稿，未实施  
> 前提：移动端 App，优先 **可解释、低延迟、零额外模型依赖**；不引入向量化模型、向量库、重排模型。

---

## 1. 设计立场

### 1.1 为什么不做语义检索（本阶段）

| 顾虑 | 说明 |
|------|------|
| 端侧算力 | 小 embedding 模型仍占内存/电量；冷启动与多 assistant 场景放大成本 |
| 依赖链 | embedding 模型 + 向量存储 +（可选）rerank → 维护与版本升级成本高 |
| 可解释性 | 面试/调试时「为什么召回这条」难以用规则说清楚 |
| 与现有架构重复 | 已有 profile 常驻 + 实体图扩图 + keyword 打分；语义检索是「锦上添花」而非「缺了不能跑」 |

**结论**：主召回路径定为 **规则 + 实体图 + 预算化注入**；现有 `MemorySemanticIndex` / `memory_embedding` 表标记为 **deprecated，不继续投入**，后续版本可删除。

### 1.2 优化目标（按优先级）

1. **Profile 归一化与冲突消解** — 解决跨 `topicKey` 重复、相近事实并存导致幻觉
2. **写入门禁** — 坏记忆、重复记忆、误晋升 profile 在入库前拦截
3. **检索增强（无向量）** — 别名表、关键词扩展、图权重、时间衰减
4. **实体质量** — 规则 NER 增强 + 可选离线 enrich（非实时 LLM）
5. **可观测与回滚** — 冲突日志、合并历史、用户可见的「记忆合并」

---

## 2. 现状痛点（对照代码）

| 痛点 | 根因 | 风险 |
|------|------|------|
| 相似稳定事实两条并存 | `inferTopicKey` 规则边界模糊（如 name vs addressing） | 模型混用、幻觉 |
| 同一事实 profile + episode 双份 | 一次命中白名单、一次未命中 | 冲突优先级难执行 |
| 预取依赖实体子串 | `detectEntitiesInUserText` 无别名 | 漏召回 |
| EVENT token 粗糙 | 兜底用正文前 12 字 | hub 噪声、误扩图 |
| 图扩展深度固定 | depth=1~2，无边权重策略 | 拉入弱相关邻居 |

---

## 3. 核心改造：Profile 归一化层（P0）

### 3.1 概念

在 `MemoryRepository.addMemory` / `updateContent` **写入 profile 之前**，增加独立模块：

```
MemoryProfileNormalizer（新）
  ├─ canonicalize(topicKey, content) → CanonicalProfileValue?
  ├─ findConflicts(assistantId, candidate) → List<ConflictHit>
  └─ resolve(candidate, policy) → WriteDecision
```

**CanonicalProfileValue**：把不同表述压到统一槽位，例如：

| 近邻 topicKey | 归一化槽位 `canonical_slot` | 示例 |
|---------------|----------------------------|------|
| `profile.name` | `identity.legal_name` | 「我叫小雨」→ 法定/常用称呼名 |
| `preference.addressing` | `identity.preferred_addressing` | 「叫我阿雨」→ 用户希望怎么称呼他 |
| `preference.like`, `preference.dislike` | `preference.item` + `polarity` | 喜欢/讨厌同一对象可检测冲突 |
| `preference.reply_style` | `interaction.reply_style` | 简短/详细 |

初期 **不合并所有近邻**，先实现 **显式槽位映射表** + **冲突检测**，避免过度自动合并。

### 3.2 写入决策 `WriteDecision`

```kotlin
sealed interface ProfileWriteDecision {
    /** 更新已有 HEAD（同 topicKey 或同 canonical_slot） */
    data class UpsertHead(val targetMemoryId: Int, val supersedeIds: List<Int>) : ProfileWriteDecision
    /** 与现有 HEAD 冲突，需用户或策略裁决 */
    data class Conflict(val hits: List<ProfileConflictHit>) : ProfileWriteDecision
    /** 无法归一化，降级为 episode */
    data class DemoteToEpisode(val reason: String) : ProfileWriteDecision
    /** 新建 profile HEAD */
    data class CreateHead(val topicKey: String) : ProfileWriteDecision
}
```

### 3.3 冲突策略（默认自动 + 可配置）

| 策略 | 行为 |
|------|------|
| `LATEST_WINS (User)` | 用户口头/显式写入 → 覆盖 HEAD；旧 HEAD 保留为 `superseded`（同时写入归一化/检索索引，保证可追溯） |
| `USER_PRIORITY` | 系统推断/Companion 同步等非用户显式来源：如存在用户 HEAD，则不覆盖（可降级为 episode 或仅写入历史，不提升为当前 HEAD） |
| （首版不做）`ASK_USER` | 不走 UI 人工裁决；仅在调试/审计时记录冲突原因（未来再扩展） |

**Prompt 层补充**（`GenerationPrompts`）：当存在未解决冲突时，注入一行：

> Profile conflict pending: do not blend; follow user's latest statement in chat.

### 3.4 数据模型扩展（Migration）

**表 `memory_profile_canonical`（新）**

| 列 | 说明 |
|----|------|
| `assistant_id` | scope |
| `canonical_slot` | 如 `identity.display_name` |
| `head_memory_id` | 当前 HEAD |
| `updated_at` | |

**表 `memory_profile_conflict`（新，可选 P1）**

| 列 | 说明 |
|----|------|
| `id` | |
| `assistant_id` | |
| `slot` | |
| `memory_id_a`, `memory_id_b` | |
| `status` | `open` / `resolved` / `ignored` |
| `resolved_by` | `user` / `auto` |

**`memory_recall_meta` 扩展（可选）**

- `source`：`user_tool` / `companion_sync` / `import`
- `confidence`：0~3

### 3.5 与现有 `topicKey` HEAD 逻辑的关系

- 保留 `getActiveByTopic(assistantId, topicKey)` 作为 **一级索引**
- `canonical_slot` 作为 **二级归一化**；写入时：
  1. `inferTopicKey`
  2. `normalizer.canonicalize`
  3. 若同 slot 已有 HEAD → `UpsertHead`（可能跨 topicKey 合并或冲突）
  4. 否则走现有 topic HEAD 逻辑

---

## 4. Episode 写入门禁（P0）

### 4.1 `MemoryWriteGate`（新）

在 `addMemory` 前检查：

| 规则 | 动作 |
|------|------|
| 与某条 ACTIVE episode 正文相似度 > 阈值（**字符 n-gram / Jaccard，非向量**） | `skip` 或 `merge`（更新 `updatedAt`） |
| 内容 < 4 字或纯标点 | `reject` |
| 命中 profile 白名单但表述含糊（「我还行」） | `demote_to_episode` 或 `reject` |
| 与 profile HEAD 完全重复 | `reject`（episode 不重复存稳定事实） |

相似度实现建议：`summaryShort` 上的 **3-gram Jaccard** 或 **Levenshtein 归一化距离**，阈值可配置，纯 Kotlin、无模型。

### 4.2 Episode → Profile 晋升（P1）

仅当：

- 同一 `canonical_slot` 的 episode 被召回 ≥ N 次，或
- 用户显式「设为长期偏好」

才触发晋升评审（仍走 `ProfileNormalizer`，不自动盲晋升）。

---

## 5. 检索增强（无向量）（P1）

### 5.1 实体别名表

**表 `memory_entity_alias`**

| `entity_id` | `alias` | `normalized` |

写入索引时：

- 「海底捞」与「国贸那家火锅」若共现 ≥ 2 次 → 建议合并或建 alias
- `detectEntitiesInUserText` 同时匹配 `name` 与 `alias`

### 5.2 关键词扩展（规则同义词表）

本地 JSON / 内置 map，例如：

```json
{
  "火锅": ["火锅店", "吃火锅"],
  "加班": ["熬夜", "很累", "压力大"]
}
```

仅用于 `recall()` 的 query 匹配加分，**不替代**实体图。

### 5.3 图检索权重

`expandFromEntity` 改进：

- 边权重：`co_occurs.weight` + 共现次数
- 节点权重：`mentionCount` + 最近 `observed_at`
- 剪枝：扩展邻居 score < hub_score * 0.3 则丢弃

### 5.4 时间衰减（episode 打分）

在 `recall()` 对 episode 加分：

```
time_boost = max(0, 10 - daysSince(observed_at) / 7)  // 一周内新鲜度加分
```

profile HEAD 不衰减。

### 5.5 预取触发扩展（P1）

当前：仅用户文本 **子串命中实体名**。

增加（无向量）：

1. 别名命中
2. 关键词扩展命中实体（「上次吃火锅」→ 扩展词命中实体「火锅」）
3. 仍 **不** 对无实体 query 做全库预取（控噪声）

---

## 6. 实体识别增强（P2）

### 6.1 规则 NER 增强（端侧、无模型）

在 `MemoryRuleIndexer` 扩展：

- 时间表达式统一规范化（已有 `observedAtHint`，可加强）
- 中文人名简易模式（姓+名 2~3 字）
- 英文 `Mr./Ms.` 模式
- 地点后缀：店/路/区/市
- **停用 EVENT token 兜底**：改为 `UNLINKED` 标记，不创建噪声节点；仅当 episode 无任何实体时仍保留图可达（用 `memory_id` 虚拟节点，不展示在 Graph UI）

### 6.2 离线 enrich（可选，非实时）

沿用现有 `MemoryRelationEnricher` + `MemoryRelationEnrichmentGate`：

| 模式 | 说明 |
|------|------|
| **默认关** | 与现网一致 |
| **开启后** | 仅在 `indexMemory` 后、后台 idle、且用户开启「智能整理记忆」时，调 **云端 LLM**（非端侧模型）补实体/关系 |
| **不做** | 每轮对话实时 enrich |

这与「不要端侧向量模型」一致：算力在服务端、按量计费、可关。

---

## 7. 语义检索退役计划（P0 文档 + P2 删代码）

| 阶段 | 动作 |
|------|------|
| P0 | 文档声明主路径为 rule+graph；DI 保持 `client=null` |
| P1 | `recall()` 内删除 `semanticBoosts` 分支或 `#ifdef` 注释 |
| P2 | 删除 `MemorySemanticIndex`、`memory_embedding` 表、Migration 反向保留数据导出 |
| 面试表述 | 「预留过语义 boost 接口，验证后认为移动端性价比低，主动收敛到可解释检索」 |

---

## 8. Prompt / 工具层（P1）

| 改动 | 目的 |
|------|------|
| Memories 块标注 `canonical_slot`（若有） | 模型知道「这是同一条稳定事实」 |
| 冲突未解决时注入 warning | 降幻觉 |
| `memory_search` 返回增加 `match_reason` | `entity_link` / `keyword` / `graph_expand` |
| Graph hubs 仅展示 **已归一化实体** | 减少 EVENT 噪声节点 |

---

## 9. UI / 产品（P2）

| 功能 | 说明 |
|------|------|
| 记忆冲突页 | 展示 `memory_profile_conflict`，用户选保留 |
| 实体合并 | 图谱页合并「小明」与「阿明」 |
| 记忆去重提示 | 「检测到相似记忆，是否合并？」 |
| 设置项 | 「智能整理记忆（云端）」开关 → `MemoryRelationEnrichmentGate` |

---

## 10. 实施分期

### Phase 0 — 规划与测试基线（1~2 周）

- [ ] 本文档评审定稿
- [ ] 补充单元测试：`ProfileNormalizer` 用例表（name/addressing 边界）
- [ ] 补充回归：`MemoryRepositoryTest` 冲突场景

### Phase 1 — Profile 归一化 + 写入门禁（2~3 周）

- [ ] `MemoryProfileNormalizer` + `memory_profile_canonical` migration
- [ ] `MemoryWriteGate`（n-gram 去重）
- [ ] 接入 `addMemory` / `updateContent`
- [ ] Prompt 冲突 warning

### Phase 2 — 检索增强（2 周）

- [ ] 实体别名表 + 预取扩展
- [ ] 关键词同义词表
- [ ] 图权重 + 时间衰减
- [ ] `memory_search` 返回 `match_reason`

### Phase 3 — 实体质量 + 退役语义（2 周）

- [ ] 规则 NER 增强 + EVENT token 策略调整
- [ ] 删除/封存 `MemorySemanticIndex`
- [ ] 图谱 UI：冲突与合并入口

### Phase 4 — 可选云端 enrich（按需）

- [ ] 设置开关 + 真实 `MemoryRelationEnricher` 实现
- [ ] 限流与失败回退规则索引

---

## 11. 成功指标

| 指标 | 目标 |
|------|------|
| 同 assistant 下 profile HEAD 重复槽位 | 0（canonical 维度） |
| episode 与 profile 完全重复条数 | 下降 80%+ |
| 预取命中率（人工标注集） | +15%（无向量） |
| 记忆相关幻觉反馈（若有埋点） | 下降 |
| 写入 P99 延迟 | 不增加 > 20ms（归一化纯规则） |
| APK 体积 / 内存 | 不引入新端侧模型 |

---

## 12. 面试可说的「为什么不做语义检索」

> 我们的用户场景是移动端私人助手，记忆量级在百条级而非百万级。Profile 常驻 + Episode 索引已经覆盖了大部分「稳定事实」和「可查目录」。语义检索需要 embedding 模型、向量存储和可选 rerank，带来包体、电量、可解释性和运维成本。我们在相同预算下优先做了 profile 归一化、实体图扩展和写入门禁——这些对幻觉和重复的改善更直接，且完全可测试、可回放。语义接口曾预留，评估后明确不继续投入。

---

## 13. 模块落点（预估文件）

```
app/src/main/java/me/rerere/rikkahub/data/memory/
  MemoryProfileNormalizer.kt      # P1
  MemoryWriteGate.kt                # P1
  MemoryKeywordExpander.kt          # P2
  MemoryRuleIndexer.kt              # 增强
  MemoryRelationEnricher.kt         # 已有，P4 实装

app/src/main/java/me/rerere/rikkahub/data/db/
  entity/MemoryProfileEntities.kt   # P1
  dao/MemoryProfileDAO.kt           # P1
  migrations/Migration_30_31.kt     # P1

app/src/main/java/me/rerere/rikkahub/data/repository/
  MemoryRepository.kt               # 接入 normalizer + gate

docs/memory/
  optimization-plan.md              # 本文
```

---

## 14. 开放问题（评审时决定）

1. `profile.name` 与 `preference.addressing`：**分立**（1A），避免把“法定/常用名”和“称呼偏好”混成同一稳定事实。
2. 冲突策略：**新覆盖旧，但保留旧信息的索引（superseded 归档可追溯）**（2）。
3. 来源优先级：**以用户权限为主**；只有当用户在对话中再次明确口头表达时才覆盖（杜绝系统“感觉”覆盖用户）（3）。
4. 实体别名：**先自动**（4），不开放用户手动合并/拆分 UI（先保证闭环和可解释日志）。

---

*文档版本：v0.1 | 与代码基线：Migration 30 / MemoryRepository recall 路径*
