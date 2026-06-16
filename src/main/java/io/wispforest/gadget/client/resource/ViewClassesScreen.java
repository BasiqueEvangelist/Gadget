package io.wispforest.gadget.client.resource;

import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.client.DialogUtil;
import io.wispforest.gadget.client.gui.GuiUtil;
import io.wispforest.gadget.client.gui.LayoutCacheWrapper;
import io.wispforest.gadget.client.gui.SubObjectContainer;
import io.wispforest.gadget.decompile.KnotUtil;
import io.wispforest.gadget.decompile.QuiltflowerHandler;
import io.wispforest.gadget.decompile.QuiltflowerManager;
import io.wispforest.gadget.early.GadgetMixinExtension;
import io.wispforest.gadget.util.ProgressToast;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.UISounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class ViewClassesScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final boolean showAll;
    private ProgressToast toast;
    private ScrollContainer<FlowLayout> contentsScroll;
    private final FlowLayout contents = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    private final QuiltflowerHandler decompiler;
    private String currentFileName = null;
    private String currentFileContents = null;


    public ViewClassesScreen(Screen parent, boolean showAll, ProgressToast toast) {
        this.parent = parent;
        this.showAll = showAll;
        this.toast = toast;

        toast.step(Component.translatable("message.gadget.progress.loading_quiltflower"));
        decompiler = QuiltflowerManager.loadHandler(toast, text -> {
            assert minecraft != null;

            minecraft.execute(() -> {
                var label = UIComponents.label(text);
                contents.child(new LayoutCacheWrapper<>(label));
                contentsScroll.scrollTo(label);
            });
        });
    }

    public static void openWithProgress(Screen parent) {
        ProgressToast toast = ProgressToast.create(Component.translatable("message.gadget.loading_classes"));
        Minecraft client = Minecraft.getInstance();
        boolean showAll = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);

        toast.follow(
            QuiltflowerManager.ensureInstalled(toast)
                .thenApplyAsync(unused -> {
                    ViewClassesScreen screen = new ViewClassesScreen(parent, showAll, toast);

                    screen.init(parent.width, parent.height);
                    screen.toast = null;

                    return screen;
                })
                .thenAcceptAsync(client::setScreenAndShow, client),
            true);
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
        contentsScroll = UIContainers.verticalScroll(Sizing.fill(72), Sizing.fill(100), contents)
            .scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0xA0FFFFFF)));

        rootComponent
            .child(treeScroll
                .margins(Insets.right(3)))
            .child(contentsScroll);

        toast.step(Component.translatable("message.gadget.progress.building_screen"));
        TreeEntry root = new TreeEntry("", tree);

        Set<String> allClasses;

        if (showAll) {
            allClasses = new TreeSet<>();

            for (Class<?> klass : KnotUtil.INSTRUMENTATION.getInitiatedClasses(Gadget.class.getClassLoader())) {
                if (klass.isHidden()) continue;
                if (klass.isArray()) continue;

                klass = klass.getNestHost();
                allClasses.add(klass.getName());
            }
        } else {
            allClasses = GadgetMixinExtension.DUMPED_CLASSES;
        }

        for (var name : allClasses) {
            String fullPath = decompiler.mapClass(name.replace('.', '/')) + ".class";
            String[] split = fullPath.split("/");
            TreeEntry parent = root;

            for (int i = 0; i < split.length - 1; i++) {
                parent = parent.directory(split[i]);
            }

            parent.container.child(makeRecipeRow(split[split.length - 1], fullPath));
        }
        toast.step(Component.literal(""));
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_S && input.hasControlDown()) {
            if (currentFileContents == null) return false;

            String path = DialogUtil.saveFileDialog(
                "Save as .java",
                currentFileName.replace(".class", ".java"),
                List.of("*.java"),
                "Java source files"
            );

            if (path != null) {
                try {
                    Files.writeString(Path.of(path), currentFileContents, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            return true;
        }

        return super.keyPressed(input);
    }

    private FlowLayout makeRecipeRow(String name, String fullPath) {
        var row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        var fileLabel = UIComponents.label(Component.literal(name));

        row.child(fileLabel);
        row.mouseEnter().subscribe(
            () -> row.surface(Surface.flat(0x80ffffff)));

        row.mouseLeave().subscribe(
            () -> row.surface(Surface.BLANK));

        row.mouseDown().subscribe((click, doubled) -> {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                UISounds.playInteractionSound();

                contents.clearChildren();

                ForkJoinPool.commonPool().execute(() -> {
                    try {
                        var text = decompiler.decompileClass(Class.forName(
                            decompiler.unmapClass(
                                fullPath
                                    .replace(".class", "")
                                    .replace('/', '.')))
                        );

                        minecraft.execute(() -> {
                            currentFileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
                            currentFileContents = text;

                            contents.configure(unused -> {
                                contents.clearChildren();
                                GuiUtil.showMonospaceText(contents, text);
                            });

                            contentsScroll.scrollTo(contents);
                        });
                    } catch (Exception e) {
                        minecraft.execute(() -> {
                            contents.configure(unused -> {
                                contents.clearChildren();
                                contents.child(GuiUtil.showException(e));
                            });
                        });
                    }
                });

            } else {
                String filename = fullPath.substring(fullPath.lastIndexOf('/') + 1);

                GuiUtil.contextMenu(row, click.x(), click.y())
                    .button(Component.translatable("text.gadget.save_as_java"), unused -> {
                        String path = DialogUtil.saveFileDialog(
                            "Save as .java",
                            filename.replace(".class", ".java"),
                            List.of("*.java"),
                            "Java source files"
                        );

                        if (path != null) {
                            try {
                                Files.writeString(Path.of(path), currentFileContents, StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                    .button(Component.translatable("text.gadget.save_as_class"), unused -> {
                        String path = DialogUtil.saveFileDialog(
                            "Save as .class",
                            filename,
                            List.of("*.class"),
                            "JVM class files"
                        );

                        if (path != null) {
                            try {
                                contents.clearChildren();
                                Files.write(
                                    Path.of(path),
                                    decompiler.getClassBytes(fullPath.replace(".class", ""))
                                );
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });

            }

            return true;
        });

        return row;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
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
