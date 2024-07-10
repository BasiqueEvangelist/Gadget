package io.wispforest.gadget.client.field;

import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.TabTextBoxComponent;
import io.wispforest.gadget.desc.PrimitiveFieldObject;
import io.wispforest.gadget.desc.edit.PrimitiveEditData;
import io.wispforest.gadget.path.ObjectPath;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PrimitiveFieldWidget extends FlowLayout {
    private final PrimitiveEditData editData;
    private final FieldDataIsland island;
    private final ObjectPath fieldPath;

    private final LabelComponent contentsLabel;
    private final LabelComponent editLabel;
    private final TextBoxComponent editField;

    protected PrimitiveFieldWidget(FieldDataIsland island, ObjectPath fieldPath, PrimitiveFieldObject pfo) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);
        this.island = island;
        this.fieldPath = fieldPath;

        this.contentsLabel = Components.label(
            Component.literal(pfo.contents())
                .withStyle(ChatFormatting.GRAY)
        );
        this.editLabel = Components.label(Component.literal(" ✎ "));
        this.editField = new TabTextBoxComponent(Sizing.fixed(100));
        this.editData = pfo.editData().orElseThrow();

        GuiUtil.semiButton(this.editLabel, this::startEditing);
        this.editField.focusLost().subscribe(this::editFieldFocusLost);
        this.editField.keyPress().subscribe(this::editFieldKeyPressed);
        this.editField
            .verticalSizing(Sizing.fixed(8));

        child(contentsLabel);
        child(editLabel);
    }

    private boolean editFieldKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            UISounds.playButtonSound();

            island.source().setPrimitiveAt(fieldPath, new PrimitiveEditData(editData.type(), editField.getValue()));

            removeChild(editField);

            child(contentsLabel);
            child(editLabel);
            return true;
        }

        return false;
    }

    private void editFieldFocusLost() {
        removeChild(editField);

        child(contentsLabel);
        child(editLabel);
    }

    private void startEditing() {
        removeChild(contentsLabel);
        removeChild(editLabel);

        child(editField);
        editField.setValue(editData.data());
        editField.moveCursorToStart(false);

        if (focusHandler() != null)
            focusHandler().focus(editField, FocusSource.MOUSE_CLICK);

        editField.setFocused(true);
    }
}
