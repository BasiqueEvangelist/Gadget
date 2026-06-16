package io.wispforest.gadget.client.nbt;

import com.mojang.serialization.DataResult;
import io.wispforest.gadget.client.ServerData;
import io.wispforest.gadget.client.gui.SidebarBuilder;
import io.wispforest.gadget.network.GadgetNetworking;
import io.wispforest.gadget.network.packet.c2s.ReplaceStackC2SPacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.Observable;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class StackComponentDataScreen extends BaseOwoScreen<FlowLayout> {
    private final NbtDataIsland island;
    private final AbstractContainerScreen<?> parent;
    private final Observable<@Nullable String> currentEncodingError = Observable.of(null);

    public StackComponentDataScreen(AbstractContainerScreen<?> parent, Slot slot) {
        var stack = slot.getItem();
        Consumer<CompoundTag> reloader = null;

        var registries = Minecraft.getInstance().level.registryAccess();

        if (ServerData.canReplaceStacks()) {
            reloader = newNbt -> {
                DataResult<DataComponentPatch> result = DataComponentPatch.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE),
                    newNbt
                );

                result
                    .ifError(error -> {
                        currentEncodingError.set(error.message());
                    })
                    .ifSuccess(newChanges -> {
                        currentEncodingError.set(null);

                        ((PatchedDataComponentMap) stack.getComponents()).restorePatch(newChanges);
//                        stack.getItem().postProcessComponents(stack);

                        if (parent instanceof CreativeModeInventoryScreen) {
                            // Let it handle it.
                            return;
                        }

                        GadgetNetworking.CHANNEL.clientHandle().send(new ReplaceStackC2SPacket(slot.index, stack));
                    });
            };
        }

        CompoundTag tag = (CompoundTag) DataComponentPatch.CODEC.encodeStart(
            registries.createSerializationContext(NbtOps.INSTANCE),
            stack.getComponentsPatch()
        )
            .getOrThrow();

        if (tag == null) tag = new CompoundTag();

        this.parent = parent;
        this.island = new NbtDataIsland(tag, reloader);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .surface(Surface.VANILLA_TRANSLUCENT);


        FlowLayout main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());

        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(95), Sizing.fill(100), main)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        rootComponent.child(scroll.child(main));

        main
            .padding(Insets.of(15));

        main.child(island);

        FlowLayout sidebar = UIContainers.verticalFlow(Sizing.content(), Sizing.content());

        if (island.reloader != null) {
            var addButton = UIContainers.verticalFlow(Sizing.fixed(16), Sizing.fixed(16))
                .child(UIComponents.label(Component.literal("+"))
                    .verticalTextAlignment(VerticalAlignment.CENTER)
                    .horizontalTextAlignment(HorizontalAlignment.CENTER)
                    .positioning(Positioning.absolute(5, 4))
                    .cursorStyle(CursorStyle.HAND)
                );

            addButton
                .cursorStyle(CursorStyle.HAND);

            addButton.mouseEnter().subscribe(
                () -> addButton.surface(Surface.flat(0x80ffffff)));

            addButton.mouseLeave().subscribe(
                () -> addButton.surface(Surface.BLANK));

            addButton.mouseDown().subscribe((click, doubled) -> {
                if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

                UISounds.playInteractionSound();

                island.typeSelector(
                    (int) (addButton.x() + click.x()),
                    (int) (addButton.y() + click.y()),
                    type -> island.child(new KeyAdderWidget(island, NbtPath.EMPTY, type, unused -> true)));

                return true;
            });

            sidebar.child(addButton);
        }

        var infoButton = new SidebarBuilder.Button(Component.translatable("text.gadget.encode_status.success"), Component.translatable("text.gadget.encode_status.success.tooltip"));

        currentEncodingError.observe(error -> {
            if (error == null) {
                infoButton.icon(Component.translatable("text.gadget.encode_status.success"));
                infoButton.tooltip(Component.translatable("text.gadget.encode_status.success.tooltip"));
            } else {
                infoButton.icon(Component.translatable("text.gadget.encode_status.failure"));
                infoButton.tooltip(Component.translatable("text.gadget.encode_status.failure.tooltip", error));
            }
        });

        sidebar.child(infoButton);

        sidebar
            .positioning(Positioning.absolute(0, 0))
            .padding(Insets.of(5));

        rootComponent.child(sidebar);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}