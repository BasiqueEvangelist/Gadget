package io.wispforest.gadget.client.field;

import com.google.gson.stream.JsonWriter;
import io.wispforest.gadget.client.gui.EventEaterWrapper;
import io.wispforest.gadget.client.gui.SaveFilePathComponent;
import io.wispforest.gadget.client.gui.SidebarBuilder;
import io.wispforest.gadget.client.gui.search.SearchGui;
import io.wispforest.gadget.field.FieldDataSource;
import io.wispforest.gadget.field.LocalFieldDataSource;
import io.wispforest.gadget.network.FieldData;
import io.wispforest.gadget.network.GadgetNetworking;
import io.wispforest.gadget.network.InspectionTarget;
import io.wispforest.gadget.network.packet.c2s.OpenFieldDataScreenC2SPacket;
import io.wispforest.gadget.path.PathStep;
import io.wispforest.gadget.util.FormattedDumper;
import io.wispforest.gadget.util.ProgressToast;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class FieldDataScreen extends BaseOwoScreen<FlowLayout> {
    private final InspectionTarget target;
    private final FieldDataSource dataSource;
    private final boolean isClient;
    public final FieldDataIsland island;

    public FieldDataScreen(InspectionTarget target, boolean isClient, boolean isMutable, @Nullable FieldData rootData, @Nullable Map<PathStep, FieldData> initialFields) {
        this.target = target;
        this.isClient = isClient;

        if (!isClient)
            dataSource = new RemoteFieldDataSource(target, rootData, initialFields);
        else
            dataSource = new LocalFieldDataSource(target.resolve(Minecraft.getInstance().level), isMutable);

        this.island = new FieldDataIsland(
            dataSource,
            false,
            true
        );
    }

    public InspectionTarget target() {
        return target;
    }

    public boolean isClient() {
        return isClient;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout verticalFlowLayout) {
        verticalFlowLayout
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .surface(Surface.VANILLA_TRANSLUCENT);


        FlowLayout main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());

        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(95), Sizing.fill(100), main)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        verticalFlowLayout.child(scroll.child(main));

        main
            .padding(Insets.of(15, 22, 15, 15));

        main.child(island.mainContainer());

        SidebarBuilder sidebar = new SidebarBuilder();

        sidebar.button(
            Component.translatable("text.gadget." + (isClient() ? "client" : "server") + "_view.icon"),
            Component.translatable("text.gadget." + (isClient() ? "client" : "server") + "_view" + (GadgetNetworking.CHANNEL.canSendToServer() ? "" : ".no_switch") + ".tooltip"),
            (mouseX, mouseY) -> {
                if (!GadgetNetworking.CHANNEL.canSendToServer()) return;

                if (isClient())
                    GadgetNetworking.CHANNEL.clientHandle().send(new OpenFieldDataScreenC2SPacket(target));
                else
                    minecraft.setScreenAndShow(new FieldDataScreen(
                        target,
                        true,
                        true, null,
                        null
                    ));
            });

        sidebar.button("text.gadget.export_button", (mouseX, mouseY) -> openExportModal());

        SearchGui search = new SearchGui(scroll);
        verticalFlowLayout
            .child(search
                .positioning(Positioning.relative(0, 100)));
        verticalFlowLayout.keyPress().subscribe((input) -> {
            if (input.key() != GLFW.GLFW_KEY_F || !input.hasControlDown())
                return false;

            uiAdapter.rootComponent.focusHandler().focus(
                search.searchBox(),
                io.wispforest.owo.ui.core.UIComponent.FocusSource.MOUSE_CLICK
            );

            return true;
        });

        verticalFlowLayout.child(sidebar.layout());
    }

    public void openExportModal() {
        FlowLayout exportModal = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        OverlayContainer<EventEaterWrapper<FlowLayout>> exportOverlay = UIContainers.overlay(new EventEaterWrapper<>(exportModal));

        exportModal
            .surface(Surface.DARK_PANEL)
            .padding(Insets.of(8));

        exportModal.child(UIComponents.label(Component.translatable("text.gadget.export.packet_dump"))
            .margins(Insets.bottom(4)));

        SaveFilePathComponent savePath =
            new SaveFilePathComponent(
                I18n.get("text.gadget.export.packet_dump"),
                FabricLoader.getInstance().getGameDir().toString() + "/"
            )
            .patterns(List.of("*.txt", "*.json"))
            .filterDescription("Plain text/JSON file");

        exportModal.child(UIContainers.horizontalFlow(Sizing.content(), Sizing.content())
            .child(UIComponents.label(Component.translatable("text.gadget.export.output_path")))
            .child(savePath)
            .verticalAlignment(VerticalAlignment.CENTER)
        );

        var button = UIComponents.button(Component.translatable("text.gadget.export.export_button"), b -> {
            var path = Path.of(savePath.path().get());

            exportOverlay.remove();

            try {
                if (path.toString().endsWith(".json")) {
                    dumpToJson(path);
                } else if (path.toString().endsWith(".txt")) {
                    dumpToPlainText(path);
                } else {
                    throw new UnsupportedOperationException("Unsupported output file type");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        savePath.path().observe(path ->
            button.active(path.endsWith(".json") || path.endsWith(".txt")));

        button.active(false);

        exportModal.child(button);

        uiAdapter.rootComponent.child(exportOverlay);
    }

    public void dumpToJson(Path path) throws IOException {
        BufferedWriter bw = Files.newBufferedWriter(path);
        JsonWriter writer = new JsonWriter(bw);

        var toast = ProgressToast.create(Component.translatable("text.gadget.exporting_field_dump"));

        CompletableFuture<Void> future =
            island.dumpToJson(
                    writer,
                    island.root(),
                    5,
                    f -> toast.step(Component.translatable("text.gadget.exporting.dumping_path", f.toString()))
                )
                .whenComplete((ignored1, ignored2) -> {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        toast.follow(future, false);
    }

    public void dumpToPlainText(Path path) throws IOException {
        var os = Files.newOutputStream(path);
        var bos = new BufferedOutputStream(os);
        FormattedDumper dumper = new FormattedDumper(new PrintStream(bos));

        dumper.write(0, "Field data of " + target);
        island.dumpToText(dumper, 0, island.root(), 5)
            .whenComplete((ignored1, ignored2) -> {
                try {
                    bos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_E && input.hasControlDown()) {
            openExportModal();

            return true;
        }

        return super.keyPressed(input);
    }

    public FieldDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void removed() {
        if (dataSource instanceof RemoteFieldDataSource remote)
            remote.close();
    }
}
