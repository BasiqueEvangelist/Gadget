package io.wispforest.gadget.decompile;

import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.util.ProgressToast;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class QuiltflowerManager {
    private static final Path DLC_DIRECTORY = FabricLoader.getInstance().getGameDir()
        .resolve("gadget")
        .resolve("dlc");

    private static SoftReference<OpenedURLClassLoader> CLASSLOADER = null;

    private QuiltflowerManager() {

    }

    public static boolean isInstalled() {
        return Files.isRegularFile(installedPath());
    }

    public static Path installedPath() {
        return DLC_DIRECTORY.resolve("quiltflower-" + QuiltflowerVersions.effectiveVersion() + ".jar");
    }


    public static CompletableFuture<Void> ensureInstalled(ProgressToast toast) {
        if (isInstalled())
            return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            toast.step(Component.translatable("text.gadget.progress.downloading_quiltflower"));

            try {
                if (!Files.isDirectory(DLC_DIRECTORY))
                    Files.createDirectories(DLC_DIRECTORY);

                String v = QuiltflowerVersions.effectiveVersion();
                String quiltflowerUrl =
                    "https://maven.quiltmc.org/repository/release/org/quiltmc/quiltflower/"
                        + v
                        + "/quiltflower-"
                        + v
                        + ".jar";

                try (var is = toast.loadWithProgress(new URL(quiltflowerUrl));
                     var os = Files.newOutputStream(installedPath())) {
                    IOUtils.copy(is, os);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static QuiltflowerHandler loadHandler(ProgressToast toast, Consumer<Component> logConsumer) {
        var cl = CLASSLOADER == null ? null : CLASSLOADER.get();
        if (cl == null) {
            try {
                var classUrl = Gadget.class.getClassLoader().getResource("io/wispforest/gadget/Gadget.class").toString();
                var dirUrl = new URL(classUrl.replace("io/wispforest/gadget/Gadget.class", ""));

                cl = new OpenedURLClassLoader(new URL[] {
                    installedPath().toUri().toURL(),
                    dirUrl
                }, Gadget.class.getClassLoader(), "io.wispforest.gadget.decompile.handle.");

                CLASSLOADER = new SoftReference<>(cl);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            var implClass = cl.loadClass("io.wispforest.gadget.decompile.handle.QuiltflowerHandlerImpl");
            return (QuiltflowerHandler) implClass.getConstructors()[0].newInstance(toast, logConsumer);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException(roe);
        }
    }
}
