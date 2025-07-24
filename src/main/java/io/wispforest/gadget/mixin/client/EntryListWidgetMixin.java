package io.wispforest.gadget.mixin.client;

import io.wispforest.gadget.Gadget;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.widget.ContainerWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin<E extends EntryListWidget.Entry<E>> extends ContainerWidget {
    @Shadow public abstract void setFocused(net.minecraft.client.gui.Element focused);

    @Shadow @Nullable protected abstract EntryListWidget.Entry<?> getEntryAtPosition(double x, double y);

    @Shadow @Nullable public abstract E getFocused();

    private EntryListWidgetMixin(int i, int j, int k, int l, Text text) {
        super(i, j, k, l, text);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!Gadget.CONFIG.rightClickDump()) {
                return false;
            }

            EntryListWidget.Entry<?> clickedEntry = this.getEntryAtPosition(mouseX, mouseY);
            if (clickedEntry != null) {
                if (clickedEntry.mouseClicked(mouseX, mouseY, button)) {
                    E parentEntry = this.getFocused();

                    if (clickedEntry != parentEntry && parentEntry instanceof ParentElement parentElement) {
                        parentElement.setFocused(null);
                    }

                    this.setFocused(clickedEntry);
                    return true;
                }
            }

            return false;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
