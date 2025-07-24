package io.wispforest.gadget.client.nbt;

import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.TabTextBoxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public class KeyAdderWidget extends FlowLayout {
    private final NbtDataIsland island;
    private final NbtPath parentPath;
    private final NbtType<?> type;
    private final Predicate<String> nameVerifier;

    private final TextBoxComponent nameField;
    private final TextBoxComponent valueField;
    private boolean wasMounted = false;

    public KeyAdderWidget(NbtDataIsland island, NbtPath parentPath, NbtType<?> type, Predicate<String> nameVerifier) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);

        this.island = island;
        this.parentPath = parentPath;
        this.type = type;
        this.nameVerifier = nameVerifier;

        child(Components.label(island.typeText(type, "")
            .append(" ")));
        child((this.nameField = new TabTextBoxComponent(Sizing.fixed(75)))
            .verticalSizing(Sizing.fixed(8)));

        if (typeNeedsValue(type)) {
            child(Components.label(Text.of(" = ")));

            child((this.valueField = new TabTextBoxComponent(Sizing.fixed(75)))
                .verticalSizing(Sizing.fixed(8)));
        } else if (typeNeedsSize(type)) {
            child(Components.label(Text.of("["))
                .margins(Insets.horizontal(2)));

            child((this.valueField = new TabTextBoxComponent(Sizing.fixed(50)))
                .verticalSizing(Sizing.fixed(8)));

            child(Components.label(Text.of("]"))
                .margins(Insets.horizontal(2)));
        } else {
            this.valueField = null;
        }

        this.nameField.keyPress().subscribe(this::onNameFieldKeyPressed);
        this.nameField.focusLost().subscribe(this::onFieldFocusLost);

        GuiUtil.textFieldVerifier(this.nameField, nameVerifier);

        if (this.valueField != null) {
            this.valueField.keyPress().subscribe(this::onValueFieldKeyPressed);
            this.valueField.focusLost().subscribe(this::onFieldFocusLost);
            GuiUtil.textFieldVerifier(this.valueField, this::verifyValue);
        }
    }

    @Override
    public void mount(ParentComponent parent, int x, int y) {
        super.mount(parent, x, y);

        if (!wasMounted) {
            wasMounted = true;

            island.focusHandler().focus(nameField, FocusSource.MOUSE_CLICK);
            nameField.setFocused(true);
        }
    }

    private void onFieldFocusLost() {
        MinecraftClient.getInstance().send(() -> {
            if (focusHandler() == null) return;

            var newFocused = focusHandler().focused();

            if (newFocused == nameField || newFocused == valueField) return;

            parent().removeChild(this);
        });
    }

    private void commit() {
        if (!nameVerifier.test(nameField.getText())) return;
        if (valueField != null && !verifyValue(valueField.getText())) return;

        NbtElement element;

        if (type == NbtCompound.TYPE)
            element = new NbtCompound();
        else if (type == NbtList.TYPE)
            element = new NbtList();
        else if (type == NbtByteArray.TYPE)
            element = new NbtByteArray(new byte[Integer.parseInt(valueField.getText())]);
        else if (type == NbtIntArray.TYPE)
            element = new NbtIntArray(new int[Integer.parseInt(valueField.getText())]);
        else if (type == NbtLongArray.TYPE)
            element = new NbtLongArray(new long[Integer.parseInt(valueField.getText())]);
        else if (type == NbtString.TYPE)
            element = NbtString.of(valueField.getText());
        else if (type == NbtByte.TYPE)
            element = NbtByte.of(Byte.parseByte(valueField.getText()));
        else if (type == NbtShort.TYPE)
            element = NbtShort.of(Short.parseShort(valueField.getText()));
        else if (type == NbtInt.TYPE)
            element = NbtInt.of(Integer.parseInt(valueField.getText()));
        else if (type == NbtLong.TYPE)
            element = NbtLong.of(Long.parseLong(valueField.getText()));
        else if (type == NbtFloat.TYPE)
            element = NbtFloat.of(Float.parseFloat(valueField.getText()));
        else if (type == NbtDouble.TYPE)
            element = NbtDouble.of(Double.parseDouble(valueField.getText()));
        else if (type == NbtEnd.TYPE)
            element = NbtEnd.INSTANCE;
        else
            throw new IllegalStateException("Unknown NbtType");

        NbtPath path = parentPath.then(nameField.getText());
        path.add(island.data, element);
        island.makeComponent(path, element);
        island.reloader.accept(island.data);

        parent().removeChild(this);
    }

    private boolean onNameFieldKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (valueField == null) {
                commit();
            } else {
                island.focusHandler().focus(valueField, FocusSource.MOUSE_CLICK);
                valueField.setFocused(true);
            }

            return true;
        }

        return false;
    }

    private boolean onValueFieldKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            commit();

            return true;
        }

        return false;
    }

    private static boolean typeNeedsValue(NbtType<?> type) {
        return type != NbtEnd.TYPE
            && type != NbtCompound.TYPE
            && type != NbtList.TYPE
            && type != NbtByteArray.TYPE
            && type != NbtIntArray.TYPE
            && type != NbtLongArray.TYPE;
    }

    private static boolean typeNeedsSize(NbtType<?> type) {
        return type == NbtByteArray.TYPE
            || type == NbtIntArray.TYPE
            || type == NbtLongArray.TYPE;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean verifyValue(String value) {
        if (type == NbtByteArray.TYPE
         || type == NbtIntArray.TYPE
         || type == NbtLongArray.TYPE) {
            return tryRun(() -> Integer.parseInt(value));
        } else if (type == NbtByte.TYPE) {
            return tryRun(() -> Byte.parseByte(value));
        } else if (type == NbtShort.TYPE) {
            return tryRun(() -> Short.parseShort(value));
        } else if (type == NbtInt.TYPE) {
            return tryRun(() -> Integer.parseInt(value));
        } else if (type == NbtLong.TYPE) {
            return tryRun(() -> Long.parseLong(value));
        } else if (type == NbtFloat.TYPE) {
            return tryRun(() -> Float.parseFloat(value));
        } else if (type == NbtDouble.TYPE) {
            return tryRun(() -> Double.parseDouble(value));
        } else {
            return true;
        }
    }

    private boolean tryRun(Runnable runnable) {
        try {
            runnable.run();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
