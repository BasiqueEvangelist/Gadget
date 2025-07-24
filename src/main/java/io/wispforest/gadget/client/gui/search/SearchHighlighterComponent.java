// Taken from https://github.com/wisp-forest/owo-lib/blob/1.19/src/main/java/io/wispforest/owo/config/ui/ConfigScreen.java.
// Includes modifications.

package io.wispforest.gadget.client.gui.search;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;

public class SearchHighlighterComponent extends BaseComponent {

    private final Color startColor = Color.ofArgb(0x008d9be0);
    private final Color endColor = Color.ofArgb(0x4c8d9be0);

    private float age = 0;

    public SearchHighlighterComponent() {
        this.positioning(Positioning.absolute(0, 0));
        this.sizing(Sizing.fill(100), Sizing.fill(100));
    }

    @Override
    public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
        final var mainColor = startColor.interpolate(endColor, (float) Math.sin(age / 25 * Math.PI)).argb();

        int segmentWidth = (int) (this.width * .3f);
        int baseX = (int) ((this.x - segmentWidth) + (Easing.CUBIC.apply(this.age / 25)) * (this.width + segmentWidth * 2));

        ctx.drawGradientRect(
            baseX - segmentWidth, this.y,
            segmentWidth, this.height,
            0, mainColor,
            mainColor, 0
        );
        ctx.drawGradientRect(
            baseX, this.y,
            segmentWidth, this.height,
            mainColor, 0,
            0, mainColor
        );
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        if ((this.age += delta) > 25) {
            this.parent.queue(() -> this.parent.removeChild(this));
        }
    }
}
