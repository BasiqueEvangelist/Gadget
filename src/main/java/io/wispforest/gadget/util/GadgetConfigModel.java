package io.wispforest.gadget.util;

import io.wispforest.gadget.decompile.QuiltflowerVersions;
import io.wispforest.gadget.mappings.LocalMappings;
import io.wispforest.gadget.mappings.Mappings;
import io.wispforest.gadget.mappings.MojangMappings;
import io.wispforest.gadget.mappings.QuiltMappings;
import io.wispforest.gadget.mappings.YarnMappings;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Hook;
import io.wispforest.owo.config.annotation.Nest;
import io.wispforest.owo.config.annotation.PredicateConstraint;
import io.wispforest.owo.config.annotation.RestartRequired;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Config(name = "gadget", wrapperName = "GadgetConfig")
public class GadgetConfigModel {
    public boolean menuButtonEnabled = true;
    public boolean rightClickDump = true;
    public boolean dropChunkData = false;
    public DumpSafetyMode dumpSafety = DumpSafetyMode.ON_EXIT;
    public boolean debugKeysInScreens = true;
    public boolean matrixStackDebugging = true;
    public boolean uiInspector = true;
    @RestartRequired public boolean silenceStartupErrors = true;
    @PredicateConstraint("isQuiltflowerVersionValid") public String quiltflowerVersion = "LATEST";
    @PredicateConstraint("isMappingsValid") @Hook public MappingsType mappings =
        FabricLoader.getInstance().isDevelopmentEnvironment() ? MappingsType.LOCAL : MappingsType.YARN;
    public UICounterMode uiCounterMode = UICounterMode.LOG_ON_LONG_UPDATE;
    public boolean inspectClasses = true;
    public boolean fullDecompilationContext = true;
    @Hook public List<String> hiddenFields = new ArrayList<>(List.of(
        "java.lang.Enum#name",

        "java.util.ArrayList#elementData",
        "java.util.ArrayList#size",
        "java.util.AbstractList#modCount",

        "java.util.AbstractMap#keySet",
        "java.util.AbstractMap#values",
        "java.util.EnumMap#entrySet",
        "java.util.EnumMap#size",
        "java.util.EnumMap#vals",
        "java.util.HashMap#table",
        "java.util.HashMap#size",
        "java.util.HashMap#modCount",
        "java.util.HashMap#loadFactor",
        "java.util.HashMap#threshold",
        "java.util.HashMap#entrySet",
        "java.util.HashMap$Node#hash",
        "java.util.HashMap$Node#next",
        "java.util.LinkedHashMap#accessOrder",
        "java.util.LinkedHashMap#head",
        "java.util.LinkedHashMap#tail",
        "java.util.LinkedHashMap$Entry#before",
        "java.util.LinkedHashMap$Entry#after",
        "java.util.IdentityHashMap#table",
        "java.util.IdentityHashMap#size",
        "java.util.IdentityHashMap#modCount",
        "java.util.IdentityHashMap#entrySet",
        "java.util.HashSet#map",
        "java.util.RegularEnumSet#elements",
        "java.util.EnumSet#elementType",
        "java.util.EnumSet#universe",

        "net.minecraft.class_2586#field_11866",
        "net.minecraft.class_2586#field_11865",
        "net.minecraft.class_2586#field_11864",

        "net.minecraft.class_1297#field_5961"
    ));
    public boolean errorCheckOwoUi = false;
    @Nest public InternalSettings internalSettings = new InternalSettings();

    public static boolean isQuiltflowerVersionValid(String version) {
        if (version.equals("LATEST")) return true;
        if (!QuiltflowerVersions.versions().contains(version)) return false;

        try {
            var v = SemanticVersion.parse(version);
            return v.compareTo((Version) SemanticVersion.parse("1.9.0")) >= 0;
        } catch (VersionParsingException e) {
            return false;
        }
    }

    public static boolean isMappingsValid(MappingsType type) {
        return type != MappingsType.LOCAL
            || FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace().equals("named");
    }

    public static class InternalSettings {
        public boolean debugMatrixStackDebugging = false;
        public boolean injectMatrixStackErrors = false;
        public boolean dumpTRMappings = false;
        public boolean dumpFieldDataRequests = false;
    }

    public enum MappingsType {
        // TODO: finish everything™
        LOCAL(() -> LocalMappings.INSTANCE),
        YARN(YarnMappings::new),
        MOJANG(MojangMappings::new),
        QUILT(QuiltMappings::new);

        private final Supplier<Mappings> factory;

        MappingsType(Supplier<Mappings> factory) {
            this.factory = factory;
        }

        public Supplier<Mappings> factory() {
            return factory;
        }
    }

    public enum UICounterMode {
        OFF,
        LOG_ON_LONG_UPDATE,
        LOG_ALWAYS
    }

    public enum DumpSafetyMode {
        ON_PAUSE(false, false),
        ON_EXIT(true, false),
        AFTER_EVERY_PACKET(true, true);

        private final boolean createExitHook;
        private final boolean flushAfterWrite;

        DumpSafetyMode(boolean createExitHook, boolean flushAfterWrite) {
            this.createExitHook = createExitHook;
            this.flushAfterWrite = flushAfterWrite;
        }

        public boolean createExitHook() {
            return createExitHook;
        }

        public boolean flushAfterWrite() {
            return flushAfterWrite;
        }
    }
}
