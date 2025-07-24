package io.wispforest.gadget.client.nbt;

import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.SubObjectContainer;
import io.wispforest.gadget.mixin.NbtTypesAccessor;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.AbstractNbtList;
import net.minecraft.nbt.AbstractNbtNumber;
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
import net.minecraft.nbt.NbtTypes;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class NbtDataIsland extends FlowLayout {
    private final Map<NbtPath, WidgetData> elements = new HashMap<>();

    final NbtCompound data;
    final @Nullable Consumer<NbtCompound> reloader;

    public NbtDataIsland(NbtCompound data, @Nullable Consumer<NbtCompound> reloader) {
        super(Sizing.content(), Sizing.content(), Algorithm.VERTICAL);

        this.data = data;
        this.reloader = reloader;

        for (String key : data.getKeys()) {
            makeComponent(new NbtPath(new String[] {key}), data.get(key));
        }
    }

    void makeComponent(NbtPath path, NbtElement element) {
        FlowLayout full = Containers.verticalFlow(Sizing.content(), Sizing.content());
        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        full.child(row);

        var parentContainer = subContainerOf(path.parent());
        WidgetData widgetData = new WidgetData();
        widgetData.fullContainer = full;

        WidgetData old = elements.get(path);
        int idx = parentContainer.children().size();

        if (parentContainer instanceof SubObjectContainer subObj)
            idx = subObj.collapsibleChildren().size();

        if (old != null) {
            int newIdx = parentContainer.children().indexOf(old.fullContainer);

            if (newIdx != -1)
                idx = newIdx;

            parentContainer.removeChild(old.fullContainer);
            elements.entrySet().removeIf(entry -> entry.getKey().startsWith(path));
        }

        elements.put(path, widgetData);

        MutableText rowText = Text.literal("");
        LabelComponent label = Components.label(rowText);

        row.child(label);

        rowText.append(typeText(element.getNbtType(), ""));
        rowText.append(" " + path.name() + " ");

        if (element instanceof NbtString string) {
            rowText.append(Text.literal("= ")
                .formatted(Formatting.GRAY));

            if (reloader != null) {
                row.child(new PrimitiveEditorWidget(this, path, string.asString(), NbtString::of));
            } else {
                rowText.append(Text.literal( string.asString() + " ")
                    .formatted(Formatting.GRAY));
            }
        } else if (element instanceof AbstractNbtNumber number) {
            rowText.append(Text.literal("= ")
                .formatted(Formatting.GRAY));

            if (reloader != null) {
                row.child(new PrimitiveEditorWidget(this, path, number.numberValue(), text -> {
                    if (number instanceof NbtByte)
                        return NbtByte.of(Byte.parseByte(text));
                    else if (number instanceof NbtShort)
                        return NbtShort.of(Short.parseShort(text));
                    else if (number instanceof NbtInt)
                        return NbtInt.of(Integer.parseInt(text));
                    else if (number instanceof NbtLong)
                        return NbtLong.of(Long.parseLong(text));
                    else if (number instanceof NbtFloat)
                        return NbtFloat.of(Float.parseFloat(text));
                    else if (number instanceof NbtDouble)
                        return NbtDouble.of(Double.parseDouble(text));
                    else
                        throw new IllegalStateException("Unknown AbstractNbtNumber type!");
                }));
            } else {
                rowText.append(Text.literal( number.numberValue() + " ")
                    .formatted(Formatting.GRAY));
            }
        } else if (element instanceof NbtCompound compound) {
            widgetData.subContainer = new SubObjectContainer(unused -> {}, unused -> {});

            row.child(widgetData.subContainer.getSpinnyBoi());

            full.child(widgetData.subContainer);

            for (String key : compound.getKeys()) {
                NbtElement sub = compound.get(key);
                var subPath = path.then(key);

                makeComponent(subPath, sub);
            }

            if (reloader != null) {
                var plusLabel = Components.label(Text.of("+ "));

                GuiUtil.semiButton(plusLabel, (mouseX, mouseY) ->
                    typeSelector(
                        (int) (plusLabel.x() + mouseX),
                        (int) (plusLabel.y() + mouseY),
                        type -> widgetData.subContainer.child(new KeyAdderWidget(this, path, type, unused -> true)))
                );

                row.child(plusLabel);
            }
        } else if (element instanceof AbstractNbtList<?> list) {
            widgetData.subContainer = new SubObjectContainer(unused -> {}, unused -> {});

            row.child(widgetData.subContainer.getSpinnyBoi());

            full.child(widgetData.subContainer);

            for (int i = 0; i < list.size(); i++) {
                NbtElement sub = list.get(i);
                var subPath = path.then(String.valueOf(i));

                makeComponent(subPath, sub);
            }

            if (reloader != null) {
                var plusLabel = Components.label(Text.of("+ "));
                Predicate<String> nameVerifier = name -> {
                    try {
                        var index = Integer.parseInt(name);

                        return index <= list.size();
                    } catch (NumberFormatException nfe) {
                        return false;
                    }
                };

                GuiUtil.semiButton(plusLabel, (mouseX, mouseY) -> {
                    if (list instanceof NbtList) {
                        if (list.isEmpty()) {
                            typeSelector(
                                (int) (plusLabel.x() + mouseX),
                                (int) (plusLabel.y() + mouseY),
                                type -> widgetData.subContainer.child(new KeyAdderWidget(this, path, type, nameVerifier)));
                        } else {
                            widgetData.subContainer.child(
                                new KeyAdderWidget(this, path, NbtTypes.byId(list.getHeldType()), nameVerifier));
                        }
                    } else if (list instanceof NbtByteArray) {
                        widgetData.subContainer.child(
                            new KeyAdderWidget(this, path, NbtByte.TYPE, nameVerifier));
                    } else if (list instanceof NbtIntArray) {
                        widgetData.subContainer.child(
                            new KeyAdderWidget(this, path, NbtInt.TYPE, nameVerifier));
                    } else if (list instanceof NbtLongArray) {
                        widgetData.subContainer.child(
                            new KeyAdderWidget(this, path, NbtLong.TYPE, nameVerifier));
                    }
                });

                row.child(plusLabel);
            }
        }

        FlowLayout target = subContainerOf(path.parent());

        if (reloader != null) {
            var crossLabel = Components.label(Text.literal("❌"));
            GuiUtil.semiButton(crossLabel, () -> {
                path.remove(data);
                reloader.accept(data);
                target.removeChild(full);
                elements.entrySet().removeIf(entry -> entry.getKey().startsWith(path));
            });
            crossLabel.margins(Insets.right(5));
            row.child(crossLabel);
        }

        var copyLabel = Components.label(Text.literal("C"));
        copyLabel.tooltip(Text.translatable("chat.copy.click"));
        GuiUtil.semiButton(copyLabel, () -> {
             MinecraftClient.getInstance().keyboard.setClipboard(path.follow(data).asString());
        });
        row.child(copyLabel);

        row
            .margins(Insets.both(0, 2))
            .allowOverflow(true);

        target.child(idx, full);
    }

    public void typeSelector(int mouseX, int mouseY, Consumer<NbtType<?>> consumer) {
        if (this.reloader == null) {
            throw new IllegalStateException("Tried to open type selector with read-only NBT island!");
        }

        var dropdown = GuiUtil.contextMenu(GuiUtil.root(this), mouseX, mouseY);

        for (NbtType<?> type : NbtTypesAccessor.getVALUES()) {
            dropdown.button(typeText(type, ".full"),
                unused -> consumer.accept(type));
        }
    }

    MutableText typeText(NbtType<?> type, String suffix) {
        String name;

        if (type == NbtCompound.TYPE)
            name = "compound";
        else if (type == NbtList.TYPE)
            name = "list";
        else if (type == NbtByteArray.TYPE)
            name = "byte_array";
        else if (type == NbtIntArray.TYPE)
            name = "int_array";
        else if (type == NbtLongArray.TYPE)
            name = "long_array";
        else if (type == NbtString.TYPE)
            name = "string";
        else if (type == NbtByte.TYPE)
            name = "byte";
        else if (type == NbtShort.TYPE)
            name = "short";
        else if (type == NbtInt.TYPE)
            name = "int";
        else if (type == NbtLong.TYPE)
            name = "long";
        else if (type == NbtFloat.TYPE)
            name = "float";
        else if (type == NbtDouble.TYPE)
            name = "double";
        else if (type == NbtEnd.TYPE)
            name = "end";
        else
            name = "unknown";

        return Text.translatable("text.gadget.nbt." + name + suffix);
    }

    public void reloadPath(NbtPath path) {
        if (path.steps().length == 0) {
            clearChildren();

            for (String key : data.getKeys()) {
                makeComponent(new NbtPath(new String[] {key}), data.get(key));
            }
        } else {
            makeComponent(path, path.follow(data));
        }
    }

    private FlowLayout subContainerOf(NbtPath path) {
        if (path.steps().length == 0)
            return this;
        else
            return elements.get(path).subContainer;
    }

    private static class WidgetData {
        private FlowLayout fullContainer;
        private SubObjectContainer subContainer;

    }

}
