package io.wispforest.gadget.desc;

import io.netty.buffer.ByteBuf;
import io.wispforest.gadget.desc.edit.PrimitiveEditData;
import io.wispforest.gadget.mappings.MappingsManager;
import io.wispforest.gadget.network.FieldData;
import io.wispforest.gadget.path.FieldPathStep;
import io.wispforest.gadget.path.IndexPathStep;
import io.wispforest.gadget.path.MapPathStep;
import io.wispforest.gadget.path.MapPathStepType;
import io.wispforest.gadget.path.PathStep;
import io.wispforest.gadget.util.NetworkUtil;
import io.wispforest.gadget.util.PrettyPrinters;
import io.wispforest.gadget.util.ReflectionUtil;
import net.auoeke.reflect.Accessor;
import net.auoeke.reflect.Fields;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

public final class FieldObjects {
    private FieldObjects() {

    }

    public static Map<PathStep, FieldData> getData(Object o, Set<Object> pathObjs, int from, int limit) {
        // TODO: make this good.
        MutableInt total = new MutableInt();
        Map<PathStep, FieldData> collected = new LinkedHashMap<>();

        FieldObjects.collectAllData(o, pathObjs, (step, data) -> {
            int i = total.getAndIncrement();

            if (limit >= 0 && i >= from + limit) return true;

            if (i >= from ) {
                collected.put(step, data);
            }

            return false;
        });

        return collected;
    }

    public static void collectAllData(Object o, Set<Object> pathObjs, BiPredicate<PathStep, FieldData> receiver) {
        if (o == null)
            return;

        if (o instanceof Iterable<?> iter) {
            int i = 0;
            boolean isFinal = ReflectionUtil.guessImmutability(iter);

            try {
                for (Object sub : iter) {
                    int idx = i++;

                    FieldObject obj = FieldObjects.fromObject(sub, pathObjs);

                    if (receiver.test(new IndexPathStep(idx), new FieldData(obj, false, isFinal)))
                        return;
                }
            } catch (UnsupportedOperationException uoe) {
                // Alright, guess you're not actually Iterable then.
            }
        }

        if (o instanceof Map<?, ?> map
         && !map.isEmpty()) {
            var oneKey = map.keySet().iterator().next();
            boolean isFinal = ReflectionUtil.guessImmutability(map);

            MapPathStepType type = MapPathStepType.getFor(oneKey.getClass());

            if (type != null) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    var path = new MapPathStep(type, type.toNetwork(entry.getKey()));

                    FieldObject obj = FieldObjects.fromObject(entry.getValue(), pathObjs);

                    if (receiver.test(path, new FieldData(obj, false, isFinal)))
                        return;
                }
            } else {
                int i = 0;
                for (Object sub : map.entrySet()) {
                    int idx = i++;
                    var path = new IndexPathStep(idx);

                    FieldObject obj = FieldObjects.fromObject(sub, pathObjs);

                    if (receiver.test(path, new FieldData(obj, false, isFinal)))
                        return;
                }
            }
        }

        if (o.getClass().isArray()) {
            int size = Array.getLength(o);

            for (int i = 0; i < size; i++) {
                var path = new IndexPathStep(i);

                FieldObject obj = FieldObjects.fromObject(Array.get(o, i), pathObjs);

                if (receiver.test(path, new FieldData(obj, false, false)))
                    return;
            }

            return;
        }

        for (Field field : (Iterable<Field>) Fields.allInstance(o.getClass()).filter(x -> !x.isSynthetic())::iterator) {
            var path = FieldPathStep.forField(field);

            FieldObject obj;

            try {
                obj = FieldObjects.fromObject(Accessor.get(o, field), pathObjs);
            } catch (Exception e) {
                obj = ErrorFieldObject.fromException(e);
            }

            boolean isMixin = field.getAnnotation(MixinMerged.class) != null;

            if (receiver.test(path, new FieldData(obj, isMixin, false)))
                return;
        }
    }


    public static FieldObject fromObject(Object o, Set<Object> pathObjs) {
        if (o instanceof NbtCompound compound) {
            return new NbtCompoundFieldObject(compound);
        } else if (o instanceof byte[] bytes) {
            return new BytesFieldObject(MappingsManager.unmapClass(o.getClass()), bytes);
        } else if (o instanceof ByteBuffer byteBuffer) {
            int position = byteBuffer.position();
            int limit = byteBuffer.limit();

            try {
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                return new BytesFieldObject(MappingsManager.unmapClass(o.getClass()), bytes);
            } finally {
                byteBuffer.position(position);
                byteBuffer.limit(limit);
            }
        } else if (o instanceof ByteBuf buf) {
            try (var ignored = NetworkUtil.resetIndexes(buf)) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new BytesFieldObject(MappingsManager.unmapClass(o.getClass()), bytes);
            }
        }

        String pretty = PrettyPrinters.tryPrint(o);

        if (pretty != null)
            return new PrimitiveFieldObject(pretty, Optional.ofNullable(PrimitiveEditData.forObject(o)));

        String tag;

        if (o instanceof ItemStack stack) {
            tag = "{" + stack.getCount() + " " + Registries.ITEM.getId(stack.getItem()) + "}";
        } else if (o.getClass().isEnum()) {
            tag = "#" + ((Enum<?>) o).name();
        } else {
            tag = "@" + Integer.toHexString(System.identityHashCode(o));
        }

        return new ComplexFieldObject(
            MappingsManager.unmapClass(o.getClass()),
            tag,
            pathObjs.contains(o)
        );
    }
}
