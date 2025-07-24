package io.wispforest.gadget.client.gui;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.DialogUtil;
import io.wispforest.gadget.client.ServerData;
import io.wispforest.gadget.client.dump.ClientPacketDumper;
import io.wispforest.gadget.client.dump.OpenDumpScreen;
import io.wispforest.gadget.client.resource.ViewClassesScreen;
import io.wispforest.gadget.client.resource.ViewResourcesScreen;
import io.wispforest.gadget.network.GadgetNetworking;
import io.wispforest.gadget.network.packet.c2s.ListResourcesC2SPacket;
import io.wispforest.gadget.util.FileUtil;
import io.wispforest.gadget.util.NumberUtil;
import io.wispforest.gadget.util.ResourceUtil;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class GadgetScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private LabelComponent inspectClasses;

    public GadgetScreen(Screen parent) {
        this.parent = parent;
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
        main.padding(Insets.of(15));

        LabelComponent openOther = Components.label(Text.translatable("text.gadget.open_other_dump"));

        openOther.margins(Insets.bottom(4));
        GuiUtil.semiButton(openOther, () -> {
            String path = DialogUtil.openFileDialog("Open other dump", null, List.of("*.dump"), "gadget network dumps", false);

            if (path != null) {
                OpenDumpScreen.openWithProgress(this, Path.of(path));
            }
        });

        main.child(openOther);

        LabelComponent inspectResources = Components.label(Text.translatable("text.gadget.inspect_resources"));

        inspectResources.margins(Insets.bottom(4));
        GuiUtil.semiButton(inspectResources,
            () -> {
                var resources = ResourceUtil.collectAllResources(client.getResourceManager());
                var map = new HashMap<Identifier, Integer>();

                for (var entry : resources.entrySet())
                    map.put(entry.getKey(), entry.getValue().size());

                var screen = new ViewResourcesScreen(this, map);

                screen.resRequester(
                    (id, idx) -> screen.openFile(
                        id, client.getResourceManager().getAllResources(id).get(idx)::getInputStream));

                client.setScreen(screen);
            });

        main.child(inspectResources);

        if (ServerData.canRequestServerData()) {
            LabelComponent inspectServerData = Components.label(Text.translatable("text.gadget.inspect_server_data"));

            inspectServerData.margins(Insets.bottom(4));
            GuiUtil.semiButton(inspectServerData,
                () -> GadgetNetworking.CHANNEL.clientHandle().send(new ListResourcesC2SPacket()));

            main.child(inspectServerData);
        }

        if (Gadget.CONFIG.inspectClasses()) {
            inspectClasses = Components.label(Text.translatable("text.gadget.inspect_exported_classes"));

            inspectClasses.margins(Insets.bottom(4));
            GuiUtil.semiButton(inspectClasses,
                () -> ViewClassesScreen.openWithProgress(this));

            main.child(inspectClasses);
        }

        try {
            if (!Files.exists(ClientPacketDumper.DUMP_DIR))
                Files.createDirectories(ClientPacketDumper.DUMP_DIR);

            for (var dump : FileUtil.listSortedByFileName(ClientPacketDumper.DUMP_DIR)) {
                String filename = dump.getFileName().toString();

                if (!filename.endsWith(".gdump")
                 && !filename.endsWith(".dump"))
                    continue;

                FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());

                Text labelText = Text.literal("")
                    .append(Text.literal("d ")
                        .formatted(Formatting.DARK_RED))
                    .append(Text.literal(filename + " "))
                    .append(Text.literal(NumberUtil.formatFileSize(Files.size(dump)) + " ")
                        .formatted(Formatting.GRAY));

                row.child(Components.label(labelText))
                    .padding(Insets.bottom(2));

                LabelComponent openLabel = Components.label(Text.translatable("text.gadget.open"));

                GuiUtil.semiButton(openLabel,
                    () -> OpenDumpScreen.openWithProgress(this, dump));

                row.child(openLabel);
                main.child(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            inspectClasses.text(Text.translatable("text.gadget.inspect_all_classes"));
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            inspectClasses.text(Text.translatable("text.gadget.inspect_exported_classes"));
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
