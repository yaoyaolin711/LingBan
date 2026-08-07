package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Hashtag
import me.rerere.hugeicons.stroke.HeartCheck
import me.rerere.hugeicons.stroke.Location01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.User02
import me.rerere.rikkahub.data.db.entity.MemoryEntityEdge
import me.rerere.rikkahub.data.db.entity.MemoryEntityNode
import me.rerere.rikkahub.data.db.entity.MemoryEntityType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.MemoryGraphSnapshot
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.SolacePalette
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantMemoryGraphPage(assistantId: String) {
    val memoryRepository: MemoryRepository = koinInject()
    var snapshot by remember { mutableStateOf<MemoryGraphSnapshot?>(null) }
    var selected by remember { mutableStateOf<MemoryEntityNode?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(assistantId) {
        snapshot = runCatching {
            memoryRepository.getGraphSnapshot(assistantId, nodeLimit = 36)
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("记忆图谱") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val data = snapshot
        if (data == null || data.nodes.isEmpty()) {
            EmptyGraphState(innerPadding)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                GraphSummaryBar(snapshot = data)
                MemoryRelationCanvas(
                    snapshot = data,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "双指缩放平移 · 点击节点查看关联记忆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
                selected?.let { node ->
                    RelatedMemoriesCard(
                        node = node,
                        memories = data.memoriesFor(node.id),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphSummaryBar(snapshot: MemoryGraphSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${snapshot.nodes.size} 个实体 · ${snapshot.resolvedEdges().size} 条关联",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GRAPH_ENTITY_TYPES.forEach { type ->
                val count = snapshot.nodes.count { it.type == type }
                if (count > 0) {
                    GraphLegendChip(type = type, count = count)
                }
            }
        }
    }
}

@Composable
private fun GraphLegendChip(type: String, count: Int) {
    val style = entityVisualStyle(type)
    Surface(
        shape = RoundedCornerShape(50),
        color = style.color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, style.color.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "${style.label} $count",
                style = MaterialTheme.typography.labelSmall,
                color = style.color,
            )
        }
    }
}

@Composable
private fun EmptyGraphState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text = "记忆图谱还是空的",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "相处多了、记下更多事后，\n人物、地点与事件会慢慢连起来。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RelatedMemoriesCard(
    node: MemoryEntityNode,
    memories: List<AssistantMemory>,
    modifier: Modifier = Modifier,
) {
    val style = entityVisualStyle(node.type)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = style.color.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.color,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${style.label} · 提及 ${node.mentionCount} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (memories.isEmpty()) {
                Text(
                    text = "暂无关联的现行记忆。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                memories.take(8).forEachIndexed { index, memory ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val statusNote = if (memory.status == "superseded") "（曾为）" else ""
                    Text(
                        text = memory.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "更新于 ${formatMemoryTime(memory.updatedAt)}$statusNote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRelationCanvas(
    snapshot: MemoryGraphSnapshot,
    selected: MemoryEntityNode?,
    onSelect: (MemoryEntityNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes = snapshot.nodes
    val edges = snapshot.resolvedEdges()
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var layout by remember { mutableStateOf<Map<Long, Offset>>(emptyMap()) }

    val selectedId = selected?.id
    val connectedIds = remember(selectedId, edges) {
        if (selectedId == null) emptySet()
        else edges.flatMap { edge ->
            when (selectedId) {
                edge.fromEntityId -> listOf(edge.toEntityId)
                edge.toEntityId -> listOf(edge.fromEntityId)
                else -> emptyList()
            }
        }.toSet()
    }

    Card(
        modifier = modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        offset += pan
                    }
                }
                .pointerInput(layout, scale, offset, nodes) {
                    detectTapGestures { tap ->
                        val hit = nodes.firstOrNull { node ->
                            val pos = layout[node.id] ?: return@firstOrNull false
                            val screen = graphToScreen(
                                graphPos = pos,
                                scale = scale,
                                offset = offset,
                                canvasCenterX = size.width / 2f,
                                canvasCenterY = size.height / 2f,
                            )
                            val hitRadius = nodeHitRadius(node) * scale
                            hypot(tap.x - screen.x, tap.y - screen.y) <= hitRadius
                        }
                        onSelect(if (hit?.id == selectedId) null else hit)
                    }
                },
        ) {
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }

            val baseLayout = remember(nodes, edges, canvasWidthPx, canvasHeightPx) {
                computeGraphLayout(
                    nodes = nodes,
                    edges = edges,
                    width = canvasWidthPx,
                    height = canvasHeightPx,
                )
            }
            layout = baseLayout

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f + offset.x
                    val cy = size.height / 2f + offset.y

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = Offset(cx, cy),
                            radius = min(size.width, size.height) * 0.45f,
                        ),
                        radius = min(size.width, size.height) * 0.48f,
                        center = Offset(cx, cy),
                    )

                    val dotStep = 28f * scale
                    val dotAlpha = 0.08f
                    var x = (cx % dotStep) - dotStep
                    while (x < size.width + dotStep) {
                        var y = (cy % dotStep) - dotStep
                        while (y < size.height + dotStep) {
                            drawCircle(
                                color = outline.copy(alpha = dotAlpha),
                                radius = 1.2f,
                                center = Offset(x, y),
                            )
                            y += dotStep
                        }
                        x += dotStep
                    }

                    edges.forEach { edge ->
                        val a = baseLayout[edge.fromEntityId] ?: return@forEach
                        val b = baseLayout[edge.toEntityId] ?: return@forEach
                        val start = Offset(cx + a.x * scale, cy + a.y * scale)
                        val end = Offset(cx + b.x * scale, cy + b.y * scale)
                        val isHighlighted = selectedId != null &&
                            (edge.fromEntityId == selectedId || edge.toEntityId == selectedId)
                        val alpha = when {
                            selectedId == null -> 0.35f
                            isHighlighted -> 0.85f
                            else -> 0.1f
                        }
                        val strokeWidth = (1.5f + edge.weight.coerceAtMost(4) * 0.5f) * scale
                        drawLine(
                            color = primary.copy(alpha = alpha),
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        if (isHighlighted) {
                            drawLine(
                                color = primary.copy(alpha = 0.2f),
                                start = start,
                                end = end,
                                strokeWidth = strokeWidth * 2.8f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    if (selectedId != null) {
                        val pos = baseLayout[selectedId] ?: return@Canvas
                        val center = Offset(cx + pos.x * scale, cy + pos.y * scale)
                        val glowRadius = nodeVisualRadius(nodes.first { it.id == selectedId }) * scale * 1.35f
                        drawCircle(
                            color = primary.copy(alpha = 0.12f),
                            radius = glowRadius,
                            center = center,
                        )
                        drawCircle(
                            color = primary.copy(alpha = 0.35f),
                            radius = glowRadius,
                            center = center,
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                            ),
                        )
                    }
                }

                nodes.forEach { node ->
                    val pos = baseLayout[node.id] ?: return@forEach
                    val screen = graphToScreen(
                        graphPos = pos,
                        scale = scale,
                        offset = offset,
                        canvasCenterX = canvasWidthPx / 2f,
                        canvasCenterY = canvasHeightPx / 2f,
                    )
                    val nodeSizePx = nodeVisualRadius(node) * 2f * scale
                    val nodeSizeDp = with(density) { nodeSizePx.toDp() }
                    val offsetXDp = with(density) { (screen.x - nodeSizePx / 2f).toDp() }
                    val offsetYDp = with(density) { (screen.y - nodeSizePx / 2f).toDp() }

                    MemoryGraphNode(
                        node = node,
                        isSelected = node.id == selectedId,
                        isConnected = node.id in connectedIds,
                        modifier = Modifier
                            .offset { IntOffset(offsetXDp.roundToPx(), offsetYDp.roundToPx()) }
                            .size(nodeSizeDp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryGraphNode(
    node: MemoryEntityNode,
    isSelected: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = entityVisualStyle(node.type)
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isSelected -> 1.12f
            isConnected -> 1.04f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nodeScale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> style.color
            isConnected -> style.color.copy(alpha = 0.7f)
            else -> style.color.copy(alpha = 0.35f)
        },
        label = "nodeBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> style.color.copy(alpha = 0.22f)
            isConnected -> style.color.copy(alpha = 0.14f)
            else -> style.color.copy(alpha = 0.1f)
        },
        label = "nodeBg",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size((44 * animatedScale).dp)
                .shadow(if (isSelected) 6.dp else 2.dp, CircleShape)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size((18 * animatedScale).dp),
            )
        }
        Text(
            text = node.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) style.color else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 2.dp)
                .fillMaxWidth(0.95f),
        )
    }
}

private data class EntityVisualStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun entityVisualStyle(type: String): EntityVisualStyle {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        MemoryEntityType.PERSON -> EntityVisualStyle(
            label = "人物",
            icon = HugeIcons.User02,
            color = scheme.primary,
        )
        MemoryEntityType.PLACE -> EntityVisualStyle(
            label = "地点",
            icon = HugeIcons.Location01,
            color = Color(0xFF5B8A72),
        )
        MemoryEntityType.EVENT -> EntityVisualStyle(
            label = "事件",
            icon = HugeIcons.Calendar03,
            color = Color(0xFFC4843A),
        )
        MemoryEntityType.PREFERENCE -> EntityVisualStyle(
            label = "偏好",
            icon = HugeIcons.HeartCheck,
            color = SolacePalette.RoseGold,
        )
        else -> EntityVisualStyle(
            label = "其他",
            icon = HugeIcons.Hashtag,
            color = scheme.onSurfaceVariant,
        )
    }
}

private val GRAPH_ENTITY_TYPES = listOf(
    MemoryEntityType.PERSON,
    MemoryEntityType.PLACE,
    MemoryEntityType.EVENT,
    MemoryEntityType.PREFERENCE,
    MemoryEntityType.OTHER,
)

private fun MemoryGraphSnapshot.resolvedEdges(): List<MemoryEntityEdge> {
    if (edges.isNotEmpty()) return edges
    val memoryToEntities = links.groupBy { it.memoryId }
    val derived = mutableListOf<MemoryEntityEdge>()
    val nodeIds = nodes.map { it.id }.toSet()
    memoryToEntities.values.forEach { group ->
        if (group.size < 2) return@forEach
        val entityIds = group.map { it.entityId }.distinct().filter { it in nodeIds }
        for (i in 0 until entityIds.lastIndex) {
            for (j in i + 1 until entityIds.size) {
                val from = min(entityIds[i], entityIds[j])
                val to = max(entityIds[i], entityIds[j])
                derived += MemoryEntityEdge(
                    assistantId = nodes.firstOrNull()?.assistantId.orEmpty(),
                    fromEntityId = from,
                    toEntityId = to,
                    relation = "co_occurs",
                    weight = 1,
                )
            }
        }
    }
    return derived.distinctBy { Triple(it.fromEntityId, it.toEntityId, it.relation) }
}

private fun computeGraphLayout(
    nodes: List<MemoryEntityNode>,
    edges: List<MemoryEntityEdge>,
    width: Float,
    height: Float,
): Map<Long, Offset> {
    if (nodes.isEmpty()) return emptyMap()
    if (nodes.size == 1) return mapOf(nodes.first().id to Offset.Zero)

    val positions = mutableMapOf<Long, Offset>()
    val maxR = min(width, height) * 0.38f
    val count = nodes.size
    nodes.forEachIndexed { index, node ->
        val angle = (2.0 * PI * index / count).toFloat()
        positions[node.id] = Offset(cos(angle) * maxR * 0.85f, sin(angle) * maxR * 0.85f)
    }

    val edgePairs = edges.map { it.fromEntityId to it.toEntityId }
    repeat(90) {
        val forces = mutableMapOf<Long, Offset>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val pa = positions.getValue(a.id)
                val pb = positions.getValue(b.id)
                val delta = pa - pb
                val dist = hypot(delta.x, delta.y).coerceAtLeast(8f)
                val repulse = 18_000f / (dist * dist)
                val dir = Offset(delta.x / dist, delta.y / dist)
                forces[a.id] = (forces[a.id] ?: Offset.Zero) + dir * repulse
                forces[b.id] = (forces[b.id] ?: Offset.Zero) - dir * repulse
            }
        }
        edgePairs.forEach { (from, to) ->
            val pa = positions[from] ?: return@forEach
            val pb = positions[to] ?: return@forEach
            val delta = pb - pa
            val dist = hypot(delta.x, delta.y).coerceAtLeast(1f)
            val target = 72f + (nodes.firstOrNull { it.id == from }?.mentionCount ?: 1) * 2f
            val attract = (dist - target) * 0.04f
            val dir = Offset(delta.x / dist, delta.y / dist)
            forces[from] = (forces[from] ?: Offset.Zero) + dir * attract
            forces[to] = (forces[to] ?: Offset.Zero) - dir * attract
        }
        nodes.forEach { node ->
            val p = positions.getValue(node.id)
            val hubPull = node.mentionCount.coerceAtLeast(1)
            forces[node.id] = (forces[node.id] ?: Offset.Zero) - p * (0.0015f * hubPull)
        }
        nodes.forEach { node ->
            val p = positions.getValue(node.id)
            val f = forces[node.id] ?: Offset.Zero
            val next = p + f
            val r = hypot(next.x, next.y).coerceAtMost(maxR)
            val ang = atan2(next.y, next.x)
            positions[node.id] = Offset(cos(ang) * r, sin(ang) * r)
        }
    }
    return positions
}

private fun nodeVisualRadius(node: MemoryEntityNode): Float {
    val base = 28f
    val boost = ln(node.mentionCount.coerceAtLeast(1).toFloat() + 1f) * 6f
    return base + boost
}

private fun nodeHitRadius(node: MemoryEntityNode): Float = nodeVisualRadius(node) + 12f

private fun graphToScreen(
    graphPos: Offset,
    scale: Float,
    offset: Offset,
    canvasCenterX: Float,
    canvasCenterY: Float,
): Offset = Offset(
    x = canvasCenterX + graphPos.x * scale + offset.x,
    y = canvasCenterY + graphPos.y * scale + offset.y,
)

private fun MemoryGraphSnapshot.memoriesFor(entityId: Long): List<AssistantMemory> {
    val ids = links.filter { it.entityId == entityId }.map { it.memoryId }.distinct()
    return ids.mapNotNull { memoriesById[it] }
}

private fun formatMemoryTime(epochMs: Long): String {
    if (epochMs <= 0L) return "未知"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}
