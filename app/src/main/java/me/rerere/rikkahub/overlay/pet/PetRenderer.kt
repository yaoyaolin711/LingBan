package me.rerere.rikkahub.overlay.pet

import androidx.compose.runtime.Composable

/**
 * 陪伴悬浮层渲染 SPI。由 [SwitchingPetRenderer] 按样式分发到头像或像素桌宠。
 */
interface PetRenderer {
    @Composable
    fun Content(
        state: CompanionPetState,
        onClick: () -> Unit,
        /** 插在桌宠与气泡之间（快捷菜单等）。 */
        besidePet: @Composable () -> Unit = {},
    )
}
