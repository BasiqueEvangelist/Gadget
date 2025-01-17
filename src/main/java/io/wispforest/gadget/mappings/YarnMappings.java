// Portions of this code are copied from NEC.
//
// Copyright (c) 2021 Fudge and NEC contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.wispforest.gadget.mappings;

import io.wispforest.gadget.util.DownloadUtil;
import io.wispforest.gadget.util.ProgressToast;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class YarnMappings extends LoadingMappings {
    private static final String YARN_API_ENTRYPOINT = "https://meta.fabricmc.net/v2/versions/yarn/" + SharedConstants.getGameVersion().getId();

    @Override
    protected void load(ProgressToast toast, MappingVisitor visitor) {
        try {
            Path mappingsDir = FabricLoader.getInstance().getGameDir().resolve("gadget").resolve("mappings");

            Files.createDirectories(mappingsDir);

            Path yarnPath = mappingsDir.resolve("yarn-" + SharedConstants.getGameVersion().getId() + ".jar");

            if (!Files.exists(yarnPath)) {
                toast.step(Text.translatable("message.gadget.progress.downloading_yarn_versions"));
                YarnVersion[] versions = DownloadUtil.read(toast, YARN_API_ENTRYPOINT, YarnVersion[].class);

                if (versions.length == 0) {
                    throw new IllegalStateException("we malden");
                }

                int latestBuild = -1;
                String latestVersion = "";

                for (YarnVersion version : versions) {
                    if (version.build > latestBuild) {
                        latestVersion = version.version;
                        latestBuild = version.build;
                    }
                }

                toast.step(Text.translatable("message.gadget.progress.downloading_yarn"));
                try (var is = toast.loadWithProgress(new URL("https://maven.fabricmc.net/net/fabricmc/yarn/" + latestVersion + "/yarn-" + latestVersion + "-v2.jar"))) {
                    FileUtils.copyInputStreamToFile(is, yarnPath.toFile());
                }
            }

            try (FileSystem fs = FileSystems.newFileSystem(yarnPath, (ClassLoader) null)) {
                try (var br = Files.newBufferedReader(fs.getPath("mappings/mappings.tiny"))) {
                    Tiny2FileReader.read(br, visitor);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class YarnVersion {
        private int build;
        private String version;
    }
}
