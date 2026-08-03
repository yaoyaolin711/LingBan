package me.rerere.rikkahub.overlay.pet

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.model.CompanionOverlayStyle

/**
 * Picks avatar or pixel pet renderer from [CompanionPetState.overlayStyle].
 */
class SwitchingPetRenderer(
    private val avatarRenderer: CompanionPetRenderer,
    private val pixelRenderer: PixelPetRenderer,
) : PetRenderer {
    @Composable
    override fun Content(
        state: CompanionPetState,
        onClick: () -> Unit,
        besidePet: @Composable () -> Unit,
    ) {
        when (state.overlayStyle) {
            CompanionOverlayStyle.PIXEL_PET -> pixelRenderer.Content(state, onClick, besidePet)
            CompanionOverlayStyle.AVATAR -> avatarRenderer.Content(state, onClick, besidePet)
        }
    }
}
