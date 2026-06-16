package io.wispforest.gadget.client.resource;

import com.mojang.blaze3d.platform.NativeImage;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.SubObjectContainer;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.UISounds;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class ViewResourcesScreen extends BaseOwoScreen<FlowLayout> {
    private static final Identifier FILE_TEXTURE_ID = Gadget.id("file_texture");

    private final Screen parent;
    private final Map<Identifier, Integer> resourcePaths;
    private BiConsumer<Identifier, Integer> resRequester;
    private DynamicTexture prevTexture;
    private FlowLayout contents;

    public ViewResourcesScreen(Screen parent, Map<Identifier, Integer> resourcePaths) {
        this.parent = parent;
        this.resourcePaths = resourcePaths;
    }

    public void resRequester(BiConsumer<Identifier, Integer> resRequester) {
        this.resRequester = resRequester;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
            .surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(5));

        FlowLayout tree = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        ScrollContainer<FlowLayout> treeScroll = UIContainers.verticalScroll(Sizing.fill(25), Sizing.fill(100), tree)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));
        contents = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        ScrollContainer<FlowLayout> contentsScroll = UIContainers.verticalScroll(Sizing.fill(72), Sizing.fill(100), contents)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        rootComponent
            .child(treeScroll
                .margins(Insets.right(3)))
            .child(contentsScroll);

        TreeEntry root = new TreeEntry("", tree);

        for (var pair : resourcePaths.entrySet()) {
            String fullPath = pair.getKey().getNamespace() + "/" + pair.getKey().getPath();
            String[] split = fullPath.split("/");
            TreeEntry parent = root;

            for (int i = 0; i < split.length - 1; i++) {
                parent = parent.directory(split[i]);
            }

            if (pair.getValue() > 1) {
                SubObjectContainer sub = new SubObjectContainer(unused -> {
                }, unused -> {
                });
                FlowLayout entryContainer = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
                FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());

                parent.container
                    .child(entryContainer
                        .child(row
                            .child(UIComponents.label(Component.literal(split[split.length - 1])))
                            .child(sub.getSpinnyBoi()
                                .margins(Insets.left(3))))
                        .child(sub));

                for (int i = 0; i < pair.getValue(); i++) {
                    sub.child(makeRecipeRow(String.valueOf(i), pair.getKey(), i));
                }

            } else {
                parent.container.child(makeRecipeRow(split[split.length - 1], pair.getKey(), 0));
            }
        }
    }

    private FlowLayout makeRecipeRow(String name, Identifier key, int index) {
        var row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        var fileLabel = UIComponents.label(Component.literal(name));

        row.child(fileLabel);
        row.mouseEnter().subscribe(
            () -> row.surface(Surface.flat(0x80ffffff)));

        row.mouseLeave().subscribe(
            () -> row.surface(Surface.BLANK));

        row.mouseDown().subscribe((click, doubled) -> {
            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

            UISounds.playInteractionSound();

            resRequester.accept(key, index);

            return true;
        });

        return row;
    }

    public void openFile(Identifier id, Callable<InputStream> isGetter) {
        if (prevTexture != null) {
            prevTexture.close();
            prevTexture = null;
        }

        contents.configure(unused -> {
            try {
                var is = isGetter.call();
                contents.clearChildren();

                if (id.getPath().endsWith(".png")) {
                    prevTexture = new DynamicTexture(id::toString, NativeImage.read(is));
                    minecraft.getTextureManager().register(FILE_TEXTURE_ID, prevTexture);

                    contents
                        .child(UIComponents.texture(
                            FILE_TEXTURE_ID,
                            0,
                            0,
                            prevTexture.getPixels().getWidth(),
                            prevTexture.getPixels().getHeight(),
                            prevTexture.getPixels().getWidth(),
                            prevTexture.getPixels().getHeight()))
                        .child(UIComponents.label(
                            Component.translatable(
                                "text.gadget.image_size",
                                prevTexture.getPixels().getWidth(),
                                prevTexture.getPixels().getHeight(),
                                "PNG"
                            )));

                    return;
                }

                is = new BufferedInputStream(is);

                boolean isText = id.getPath().endsWith(".txt")
                    || id.getPath().endsWith(".json")
                    || id.getPath().endsWith(".fsh")
                    || id.getPath().endsWith(".vsh")
                    || id.getPath().endsWith(".snbt");

                if (!isText) {
                    try {
                        is.mark(128);
                        byte[] bytes = is.readNBytes(128);

                        var chars = StandardCharsets.UTF_8
                            .newDecoder()
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes));

                        isText = true;

                        for (int i = 0; i < chars.length(); i++) {
                            int codepoint = chars.charAt(i);

                            if (codepoint > 127) continue;

                            if (!Character.isDigit(codepoint)
                                && !Character.isAlphabetic(codepoint)
                                && !Character.isSpaceChar(codepoint)) {
                                isText = false;
                                break;
                            }
                        }
                    } catch (CharacterCodingException cce) {
                        // ...
                    }

                    is.reset();
                }

                if (isText) {
                    GuiUtil.showMonospaceText(contents, new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    return;
                }

                // Display as bytes.
                contents.child(GuiUtil.hexDump(is.readAllBytes(), false));
            } catch (Exception e) {
                contents.child(GuiUtil.showException(e));
            }
        });
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
        if (prevTexture != null) {
            prevTexture.close();
        }
    }

    private static class TreeEntry {
        private final String name;
        private final List<TreeEntry> children = new ArrayList<>();
        private final FlowLayout container;

        private TreeEntry(String name, FlowLayout container) {
            this.name = name;
            this.container = container;
        }

        public TreeEntry directory(String name) {
            for (TreeEntry entry : children)
                if (entry.name.equals(name))
                    return entry;

            SubObjectContainer sub = new SubObjectContainer(unused -> {
            }, unused -> {
            });
            FlowLayout entryContainer = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
            FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());

            container
                .child(entryContainer
                    .child(row
                        .child(UIComponents.label(Component.literal(name)))
                        .child(sub.getSpinnyBoi()
                            .margins(Insets.left(3))))
                    .child(sub));

            TreeEntry entry = new TreeEntry(name, sub);
            children.add(entry);
            return entry;
        }
    }
}
