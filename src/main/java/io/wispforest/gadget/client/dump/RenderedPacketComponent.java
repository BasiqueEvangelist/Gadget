package io.wispforest.gadget.client.dump;

import io.wispforest.gadget.client.dump.handler.PacketRenderer;
import io.wispforest.gadget.client.gui.BasedLabelComponent;
import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.LayoutCacheWrapper;
import io.wispforest.gadget.client.gui.SubObjectContainer;
import io.wispforest.gadget.dump.fake.GadgetReadErrorPacket;
import io.wispforest.gadget.dump.fake.GadgetWriteErrorPacket;
import io.wispforest.gadget.dump.read.DumpReaderContext;
import io.wispforest.gadget.dump.read.DumpedPacket;
import io.wispforest.gadget.dump.read.SearchTextData;
import io.wispforest.gadget.dump.read.UnwrappedPacketData;
import io.wispforest.gadget.util.ContextData;
import io.wispforest.gadget.util.ReflectionUtil;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

public class RenderedPacketComponent {
    public static final ContextData.Key<DumpedPacket, RenderedPacketComponent> KEY = new ContextData.Key<>(RenderedPacketComponent::new);

    private final DumpedPacket packet;
    private SoftReference<Component> component;
    private final List<Throwable> drawErrors = new ArrayList<>();

    public RenderedPacketComponent(DumpedPacket packet) {
        this.packet = packet;
    }

    public List<Throwable> drawErrors() {
        return drawErrors;
    }

    public Component component() {
        if (component == null || component.get() == null) {
            FlowLayout view = Containers.verticalFlow(Sizing.content(), Sizing.content());

            view
                .padding(Insets.of(5))
                .surface(Surface.outline(packet.color()))
                .margins(Insets.bottom(5));

            MutableText typeText = Text.literal("");

            if (packet.packet() instanceof GadgetReadErrorPacket errorPacket) {
                typeText.append(Text.translatable("text.gadget.packet_read_error", errorPacket.packetId()));
            } else if (packet.packet() instanceof GadgetWriteErrorPacket errorPacket) {
                typeText.append(Text.translatable("text.gadget.packet_write_error", errorPacket.packetId()));
            } else {
                typeText.append(ReflectionUtil.nameWithoutPackage(packet.packet().getClass()));

                if (packet.channelId() != null)
                    typeText.append(Text.literal(" " + packet.channelId())
                        .formatted(Formatting.GRAY));
            }

            var readerCtx = packet.get(DumpReaderContext.KEY);

            if (readerCtx != null) {
                typeText.append(" ");

                typeText.append(Text.literal(DurationFormatUtils.formatDuration(packet.sentAt() - readerCtx.reader().startTime(), "mm:ss.SSS"))
                    .formatted(Formatting.DARK_GRAY));
            }

            var container = new SubObjectContainer(unused -> {}, unused -> {});

            container
                .padding(Insets.of(0))
                .surface(Surface.BLANK);

            container.toggleExpansion();

            view.child(Containers.horizontalFlow(Sizing.content(), Sizing.content())
                .child(new BasedLabelComponent(typeText))
                .child(container.getSpinnyBoi())
                .margins(Insets.bottom(3)));


            drawErrors.clear();
            PacketRenderer.EVENT.invoker().renderPacket(packet, container, drawErrors::add);

            if (!drawErrors.isEmpty()
                || packet.packet() instanceof GadgetReadErrorPacket
                || packet.packet() instanceof GadgetWriteErrorPacket) {
                CollapsibleContainer errors = Containers.collapsible(
                    Sizing.content(),
                    Sizing.content(),
                    Text.translatable("text.gadget.packet_errors"),
                    false
                );

                errors
                    .padding(Insets.of(2))
                    .margins(Insets.bottom(5));

                ((FlowLayout) errors.children().get(0))
                    .padding(Insets.of(2, 2, 2, 0));

                if (packet.packet() instanceof GadgetReadErrorPacket error) {
                    errors.child(GuiUtil.showException(error.exception())
                        .margins(Insets.bottom(2)));
                }

                if (packet.packet() instanceof GadgetWriteErrorPacket error) {
                    errors.child(GuiUtil.showExceptionText(error.exceptionText())
                        .margins(Insets.bottom(2)));
                }

                for (var e : packet.get(SearchTextData.KEY).searchTextErrors()) {
                    errors.child(GuiUtil.showException(e)
                        .margins(Insets.bottom(2)));
                }

                for (var e : packet.get(RenderedPacketComponent.KEY).drawErrors()) {
                    errors.child(GuiUtil.showException(e)
                        .margins(Insets.bottom(2)));
                }

                for (var e : packet.get(UnwrappedPacketData.KEY).errors()) {
                    errors.child(GuiUtil.showException(e)
                        .margins(Insets.bottom(2)));
                }

                container.child(0, errors);
            }

            view.child(container);

            FlowLayout fullRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());

            fullRow
                .child(view)
                .horizontalAlignment(packet.outbound() ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);

            component = new SoftReference<>(new LayoutCacheWrapper<>(fullRow));
        }

        return component.get();
    }
}
