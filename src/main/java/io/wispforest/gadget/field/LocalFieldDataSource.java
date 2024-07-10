package io.wispforest.gadget.field;

import io.wispforest.gadget.desc.FieldObjects;
import io.wispforest.gadget.desc.edit.PrimitiveEditData;
import io.wispforest.gadget.network.FieldData;
import io.wispforest.gadget.path.ObjectPath;
import io.wispforest.gadget.path.PathStep;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public record LocalFieldDataSource(Object target, boolean isMutable) implements FieldDataSource {
    @Override
    public FieldData rootData() {
        return new FieldData(FieldObjects.fromObject(target, Set.of()), false, true);
    }

    @Override
    public Map<PathStep, FieldData> initialRootFields() {
        return FieldObjects.getData(target, ReferenceSets.singleton(target), 0, -1);
    }

    @Override
    public CompletableFuture<Map<PathStep, FieldData>> requestFieldsOf(ObjectPath path, int from, int limit) {
        Object[] real = path.toRealPath(target);

        return CompletableFuture.completedFuture(FieldObjects.getData(real[real.length - 1], new ReferenceOpenHashSet<>(real), from, limit));
    }

    @Override
    public boolean isMutable() {
        return isMutable;
    }

    @Override
    public CompletableFuture<Void> setPrimitiveAt(ObjectPath path, PrimitiveEditData editData) {
        if (!isMutable())
            throw new UnsupportedOperationException();

        path.set(target, editData.toObject());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setCompoundTagAt(ObjectPath path, CompoundTag tag) {
        if (!isMutable())
            throw new UnsupportedOperationException();

        path.set(target, tag);

        return CompletableFuture.completedFuture(null);
    }
}
