package io.wispforest.gadget.client.field;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.SubObjectContainer;
import io.wispforest.gadget.client.gui.search.SearchAnchorComponent;
import io.wispforest.gadget.client.nbt.KeyAdderWidget;
import io.wispforest.gadget.client.nbt.NbtDataIsland;
import io.wispforest.gadget.client.nbt.NbtPath;
import io.wispforest.gadget.desc.BytesFieldObject;
import io.wispforest.gadget.desc.ComplexFieldObject;
import io.wispforest.gadget.desc.ErrorFieldObject;
import io.wispforest.gadget.desc.NbtCompoundFieldObject;
import io.wispforest.gadget.desc.PrimitiveFieldObject;
import io.wispforest.gadget.field.FieldDataHolder;
import io.wispforest.gadget.field.FieldDataSource;
import io.wispforest.gadget.network.FieldData;
import io.wispforest.gadget.path.FieldPathStep;
import io.wispforest.gadget.path.ObjectPath;
import io.wispforest.gadget.path.PathStep;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class FieldDataIsland extends FieldDataHolder<ClientFieldDataNode> {
    private final FlowLayout mainContainer;
    private final boolean generateAnchors;

    public FieldDataIsland(FieldDataSource source, boolean shortenNames, boolean generateAnchors) {
        super(source, shortenNames);
        this.generateAnchors = generateAnchors;

        init();

        this.mainContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());

        addChildrenTo(mainContainer, root.childrenOrNull());
    }

    public FlowLayout mainContainer() {
        return mainContainer;
    }

    @Override
    protected ClientFieldDataNode createNodeFrom(ObjectPath path, FieldData data) {
        ClientFieldDataNode node = new ClientFieldDataNode(this, path, data);

        node.containerComponent = Containers.verticalFlow(Sizing.content(), Sizing.content());

        if (path.steps().length > 0) {
            var row = Containers.horizontalFlow(Sizing.content(), Sizing.content());

            node.containerComponent.child(row);

            if (generateAnchors) {
                SearchAnchorComponent anchor = new SearchAnchorComponent(row, path::name);
                row.child(anchor);
            }

            var nameText = Text.literal(path.name());

            if (data.isMixin())
                nameText.formatted(Formatting.GRAY)
                    .styled(x -> x.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Mixin-injected field")
                            .formatted(Formatting.YELLOW))));

            MutableText rowText = Text.literal("")
                .append(Text.literal(data.obj().type().charAt(0) + " ")
                    .styled(x -> x.withColor(data.obj().color())))
                .append(nameText);
            var rowLabel = Components.label(rowText);

            row.child(rowLabel);

            if (data.obj() instanceof PrimitiveFieldObject pfo) {
                if (!data.isFinal() && source.isMutable() && pfo.editData().isPresent()) {
                    rowText.append(Text.literal(" = ")
                        .formatted(Formatting.GRAY));
                    row.child(new PrimitiveFieldWidget(this, path, pfo));
                } else {
                    rowText.append(Text.literal(" = " + pfo.contents())
                        .formatted(Formatting.GRAY));
                }
            } else if (data.obj() instanceof ErrorFieldObject efo) {
                rowText.append(Text.literal(" " + efo.exceptionClass())
                    .styled(x -> x
                        .withColor(Formatting.RED)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(efo.fullExceptionText())))));
            } else if (data.obj() instanceof ComplexFieldObject cfo) {
                var subContainer = new SubObjectContainer(
                    container -> {
                        node.ensureChildren()
                            .thenAcceptAsync(newChildren -> addChildrenTo(container, newChildren),
                                MinecraftClient.getInstance());
                    },
                    SubObjectContainer::clearChildren);
                node.subObjectContainer = subContainer;
                node.containerComponent.child(subContainer);

                String text = cfo.text();

                if (shortenNames)
                    text = text.substring(text.lastIndexOf('.') + 1);

                rowText.append(
                    Text.literal(" " + text + " ")
                        .formatted(Formatting.GRAY)
                );

                if (!cfo.isRepeat()) {
                    row
                        .child(subContainer.getSpinnyBoi()
                            .sizing(Sizing.fixed(10), Sizing.content()));
                }
            } else if (data.obj() instanceof NbtCompoundFieldObject nfo) {
                var subContainer = new SubObjectContainer(
                    unused -> {
                    },
                    unused -> {
                    });
                node.subObjectContainer = subContainer;
                node.containerComponent.child(subContainer);

                Consumer<NbtCompound> reloader = null;

                if (source.isMutable())
                    reloader = newData -> source.setNbtCompoundAt(path, newData);

                var island = new NbtDataIsland(nfo.data(), reloader);

                subContainer.child(island);

                row
                    .child(subContainer.getSpinnyBoi()
                        .sizing(Sizing.fixed(10), Sizing.content()));

                if (source.isMutable()) {
                    var plusLabel = Components.label(Text.of("+"));

                    GuiUtil.semiButton(plusLabel, (mouseX, mouseY) ->
                        island.typeSelector(
                            (int) (plusLabel.x() + mouseX),
                            (int) (plusLabel.y() + mouseY),
                            type -> subContainer.child(new KeyAdderWidget(island, NbtPath.EMPTY, type, unused -> true)))
                    );

                    row.child(plusLabel);
                }
            } else if (data.obj() instanceof BytesFieldObject bfo) {
                var subContainer = new SubObjectContainer(
                    unused -> {
                    },
                    unused -> {
                    });
                node.subObjectContainer = subContainer;
                node.containerComponent.child(subContainer);

                rowText.append(
                    Text.literal(" " + bfo.text() + " ")
                        .formatted(Formatting.GRAY)
                );

                subContainer.child(GuiUtil.hexDump(bfo.data(), false));

                row
                    .child(subContainer.getSpinnyBoi()
                        .sizing(Sizing.fixed(10), Sizing.content()));
            }

            if (path.last() instanceof FieldPathStep step) {
                rowLabel.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

                    GuiUtil.contextMenu(rowLabel, mouseX, mouseY)
                        .button(Text.translatable("text.gadget.hide_field"), unused -> {
                            ArrayList<String> hiddenFields = new ArrayList<>(Gadget.CONFIG.hiddenFields());
                            hiddenFields.add(step.fieldId());
                            Gadget.CONFIG.hiddenFields(hiddenFields);
                        });

                    return true;
                });
            }

            row
                .margins(Insets.both(0, 2))
                .allowOverflow(true);
        }

        return node;
    }

    private void addChildrenTo(FlowLayout container, Map<PathStep, ClientFieldDataNode> children) {
        container.configure(ignored -> {
            for (var entry : children.entrySet()) {
                if (entry.getKey() instanceof FieldPathStep step
                    && Gadget.CONFIG.hiddenFields().contains(step.fieldId())) {
                    continue;
                }

                container.child(entry.getValue().containerComponent);
            }
        });
    }
}
