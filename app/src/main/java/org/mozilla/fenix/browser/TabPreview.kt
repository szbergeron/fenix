package org.mozilla.fenix.browser

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.tab_preview.view.*
import kotlinx.android.synthetic.main.tab_preview_top_bar.view.*
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.support.images.ImageRequest
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.theme.ThemeManager

class TabPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val thumbnailLoader = ThumbnailLoader(context.components.core.thumbnailStorage)

    private val inflater = LayoutInflater.from(context)

    private val toolbar: View

    init {
        inflater.inflate(R.layout.tab_preview, this, true)
        inflater.inflate(
            if (context.settings().shouldUseBottomToolbar) {
                R.layout.tab_preview_bottom_bar
            } else {
                R.layout.tab_preview_top_bar
            }, this, true
        )
        toolbar = findViewById(R.id.fakeToolbar)

        menuButton.setColorFilter(
            ContextCompat.getColor(
                context,
                ThemeManager.resolveAttribute(R.attr.primaryText, context)
            )
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        previewThumbnail.translationY = if (!context.settings().shouldUseBottomToolbar) {
            toolbar.height.toFloat()
        } else {
            0f
        }
    }

    fun loadPreviewThumbnail(thumbnailId: String) {
        thumbnailLoader.loadIntoView(
            view = previewThumbnail,
            request = ImageRequest(id = thumbnailId, size = ImageRequest.Size.MAX)
        )
    }
}
