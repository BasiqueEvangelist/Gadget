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
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class GadgetScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private LabelComponent inspectClasses;

    public GadgetScreen(Screen parent) {
        this.parent = parent;
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
        main.padding(Insets.of(15));

        LabelComponent openOther = UIComponents.label(Component.translatable("text.gadget.open_other_dump"));

        openOther.margins(Insets.bottom(4));
        GuiUtil.semiButton(openOther, () -> {
            String path = DialogUtil.openFileDialog("Open other dump", null, List.of("*.dump"), "gadget network dumps", false);

            if (path != null) {
                OpenDumpScreen.openWithProgress(this, Path.of(path));
            }
        });

        main.child(openOther);

        LabelComponent inspectResources = UIComponents.label(Component.translatable("text.gadget.inspect_resources"));

        inspectResources.margins(Insets.bottom(4));
        GuiUtil.semiButton(inspectResources,
            () -> {
                var resources = ResourceUtil.collectAllResources(minecraft.getResourceManager());
                var map = new HashMap<Identifier, Integer>();

                for (var entry : resources.entrySet())
                    map.put(entry.getKey(), entry.getValue().size());

                var screen = new ViewResourcesScreen(this, map);

                screen.resRequester(
                    (id, idx) -> screen.openFile(
                        id, minecraft.getResourceManager().getResourceStack(id).get(idx)::open));

                minecraft.setScreenAndShow(screen);
            });

        main.child(inspectResources);

        if (ServerData.canRequestServerData()) {
            LabelComponent inspectServerData = UIComponents.label(Component.translatable("text.gadget.inspect_server_data"));

            inspectServerData.margins(Insets.bottom(4));
            GuiUtil.semiButton(inspectServerData,
                () -> GadgetNetworking.CHANNEL.clientHandle().send(new ListResourcesC2SPacket()));

            main.child(inspectServerData);
        }

        if (Gadget.CONFIG.inspectClasses()) {
            inspectClasses = UIComponents.label(Component.translatable("text.gadget.inspect_exported_classes"));

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

                FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());

                Component labelText = Component.literal("")
                    .append(Component.literal("d ")
                        .withStyle(ChatFormatting.DARK_RED))
                    .append(Component.literal(filename + " "))
                    .append(Component.literal(NumberUtil.formatFileSize(Files.size(dump)) + " ")
                        .withStyle(ChatFormatting.GRAY));

                row.child(UIComponents.label(labelText))
                    .padding(Insets.bottom(2));

                LabelComponent openLabel = UIComponents.label(Component.translatable("text.gadget.open"));

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
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_LEFT_SHIFT || input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            inspectClasses.text(Component.translatable("text.gadget.inspect_all_classes"));
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_LEFT_SHIFT || input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            inspectClasses.text(Component.translatable("text.gadget.inspect_exported_classes"));
        }

        return super.keyReleased(input);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
