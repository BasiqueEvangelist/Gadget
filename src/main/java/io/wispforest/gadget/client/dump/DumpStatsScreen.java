package io.wispforest.gadget.client.dump;

import io.wispforest.gadget.client.gui.BasedVerticalFlowLayout;
import io.wispforest.gadget.client.gui.SidebarBuilder;
import io.wispforest.gadget.dump.read.PacketDumpReader;
import io.wispforest.gadget.dump.read.SearchTextData;
import io.wispforest.gadget.util.NumberUtil;
import io.wispforest.gadget.util.ProgressToast;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.mutable.MutableLong;
import org.jetbrains.annotations.NotNull;

public class DumpStatsScreen extends BaseOwoScreen<FlowLayout> {
    private final Map<String, PacketTypeData> packetTypes = new HashMap<>();
    private final Screen parent;
    private final PacketDumpReader reader;
    private int totalSize = 0;

    public DumpStatsScreen(Screen parent, PacketDumpReader reader, ProgressToast toast) {
        this.parent = parent;
        this.reader = reader;

        MutableLong progress = new MutableLong(0);
        toast.followProgress(progress::getValue, reader.packets().size());
        for (var packet : reader.packets()) {
            var type = packetTypes.computeIfAbsent(packet.get(SearchTextData.KEY).searchText(), unused -> new PacketTypeData());
            type.total += 1;
            type.size += packet.size();
            totalSize += packet.size();

            progress.add(1);
        }
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

        FlowLayout main = new BasedVerticalFlowLayout(Sizing.fill(100), Sizing.content());
        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(95), Sizing.fill(100), main)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        main.padding(Insets.vertical(15));

        packetTypes
            .entrySet()
            .stream()
            .sorted(Comparator.comparing(x -> -x.getValue().size))
            .forEachOrdered(x -> {
                double sizePercent = (double) x.getValue().size / totalSize;
                double totalPercent = (double) x.getValue().total / reader.packets().size();

                MutableComponent total = Component.literal(x.getKey())
                    .append(Component.literal(" " + x.getValue().total + " packets,")
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + NumberUtil.formatFileSize(x.getValue().size) + " total")
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n  " + NumberUtil.formatPercent(sizePercent) + " of size"))
                    .append(Component.literal("\n  " + NumberUtil.formatPercent(totalPercent) + " of packets"));

                main.child(UIComponents.label(total)
                    .margins(Insets.bottom(3)));
            });

        SidebarBuilder sidebar = new SidebarBuilder();

        sidebar.button("text.gadget.back", (mouseX, mouseY) -> onClose());

        rootComponent
            .child(scroll)
            .child(sidebar.layout());
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private static class PacketTypeData {
        private int total;
        private int size;
    }
}
