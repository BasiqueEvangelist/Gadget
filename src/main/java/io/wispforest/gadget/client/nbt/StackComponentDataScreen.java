package io.wispforest.gadget.client.nbt;

import com.mojang.serialization.DataResult;
import io.wispforest.gadget.client.ServerData;
import io.wispforest.gadget.client.gui.SidebarBuilder;
import io.wispforest.gadget.network.GadgetNetworking;
import io.wispforest.gadget.network.packet.c2s.ReplaceStackC2SPacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.Observable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class StackComponentDataScreen extends BaseOwoScreen<FlowLayout> {
    private final NbtDataIsland island;
    private final HandledScreen<?> parent;
    private final Observable<@Nullable String> currentEncodingError = Observable.of(null);

    public StackComponentDataScreen(HandledScreen<?> parent, Slot slot) {
        var stack = slot.getStack();
        Consumer<NbtCompound> reloader = null;

        var registries = MinecraftClient.getInstance().world.getRegistryManager();

        if (ServerData.canReplaceStacks()) {
            reloader = newNbt -> {
                DataResult<ComponentChanges> result = ComponentChanges.CODEC.parse(
                    registries.getOps(NbtOps.INSTANCE),
                    newNbt
                );

                result
                    .ifError(error -> {
                        currentEncodingError.set(error.message());
                    })
                    .ifSuccess(newChanges -> {
                        currentEncodingError.set(null);

                        ((MergedComponentMap) stack.getComponents()).setChanges(newChanges);
                        stack.getItem().postProcessComponents(stack);

                        if (parent instanceof CreativeInventoryScreen) {
                            // Let it handle it.
                            return;
                        }

                        GadgetNetworking.CHANNEL.clientHandle().send(new ReplaceStackC2SPacket(slot.id, stack));
                    });
            };
        }

        NbtCompound tag = (NbtCompound) ComponentChanges.CODEC.encodeStart(
            registries.getOps(NbtOps.INSTANCE),
            stack.getComponentChanges()
        )
            .getOrThrow();

        if (tag == null) tag = new NbtCompound();

        this.parent = parent;
        this.island = new NbtDataIsland(tag, reloader);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .surface(Surface.VANILLA_TRANSLUCENT);


        FlowLayout main = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(95), Sizing.fill(100), main)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        rootComponent.child(scroll.child(main));

        main
            .padding(Insets.of(15));

        main.child(island);

        FlowLayout sidebar = Containers.verticalFlow(Sizing.content(), Sizing.content());

        if (island.reloader != null) {
            var addButton = Containers.verticalFlow(Sizing.fixed(16), Sizing.fixed(16))
                .child(Components.label(Text.literal("+"))
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

            addButton.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

                UISounds.playInteractionSound();

                island.typeSelector(
                    (int) (addButton.x() + mouseX),
                    (int) (addButton.y() + mouseY),
                    type -> island.child(new KeyAdderWidget(island, NbtPath.EMPTY, type, unused -> true)));

                return true;
            });

            sidebar.child(addButton);
        }

        var infoButton = new SidebarBuilder.Button(Text.translatable("text.gadget.encode_status.success"), Text.translatable("text.gadget.encode_status.success.tooltip"));

        currentEncodingError.observe(error -> {
            if (error == null) {
                infoButton.icon(Text.translatable("text.gadget.encode_status.success"));
                infoButton.tooltip(Text.translatable("text.gadget.encode_status.success.tooltip"));
            } else {
                infoButton.icon(Text.translatable("text.gadget.encode_status.failure"));
                infoButton.tooltip(Text.translatable("text.gadget.encode_status.failure.tooltip", error));
            }
        });

        sidebar.child(infoButton);

        sidebar
            .positioning(Positioning.absolute(0, 0))
            .padding(Insets.of(5));

        rootComponent.child(sidebar);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
