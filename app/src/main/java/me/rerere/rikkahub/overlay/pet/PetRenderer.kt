package me.rerere.rikkahub.overlay.pet

import androidx.compose.runtime.Composable

/**
 * 陪伴悬浮头像渲染 SPI。默认 [CompanionPetRenderer]。
 */
interface PetRenderer {
    @Composable
    fun Content(state: CompanionPetState, onClick: () -> Unit)
}
