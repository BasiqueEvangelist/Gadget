package io.wispforest.gadget.field;

import com.google.gson.stream.JsonWriter;
import io.wispforest.gadget.Gadget;
import io.wispforest.gadget.desc.BytesFieldObject;
import io.wispforest.gadget.desc.ComplexFieldObject;
import io.wispforest.gadget.desc.ErrorFieldObject;
import io.wispforest.gadget.desc.NbtCompoundFieldObject;
import io.wispforest.gadget.desc.PrimitiveFieldObject;
import io.wispforest.gadget.mappings.MappingsManager;
import io.wispforest.gadget.network.FieldData;
import io.wispforest.gadget.path.FieldPathStep;
import io.wispforest.gadget.path.ObjectPath;
import io.wispforest.gadget.path.PathStep;
import io.wispforest.gadget.util.FormattedDumper;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public abstract class FieldDataHolder<N extends FieldDataNode<N>> {
    protected final FieldDataSource source;
    protected final boolean shortenNames;
    protected N root = null;

    public FieldDataHolder(FieldDataSource source, boolean shortenNames) {
        this.source = source;
        this.shortenNames = shortenNames;
    }

    protected void init() {
        this.root = createNodeFrom(ObjectPath.EMPTY, source.rootData());
        this.root.initChildren(processMap(ObjectPath.EMPTY, source.initialRootFields()));
    }

    public FieldDataSource source() {
        return source;
    }

    public N root() {
        return root;
    }

    public N get(ObjectPath path) {
        N current = root;

        for (PathStep step : path.steps()) {
            var children = current.childrenOrNull();

            if (children == null) return null;

            current = children.get(step);

            if (current == null) return null;
        }

        return current;
    }

    public CompletableFuture<Void> dumpToText(FormattedDumper dumper, int indent, N startingAt, int depthLeft) {
        return startingAt
            .ensureChildren()
            .thenCompose(children -> {
                CompletableFuture<Void> runningTotal = CompletableFuture.completedFuture(null);

                for (var entry : children.entrySet()) {
                    runningTotal = runningTotal.thenCompose(ignored -> {
                        StringBuilder head = new StringBuilder();
                        var data = entry.getValue();

                        head.append(data.fieldObj().type().charAt(0));
                        head.append(" ");
                        head.append(entry.getKey().toString());

                        if (data.isMixin())
                            head.append(" (mixin)");

                        if (data.fieldObj() instanceof PrimitiveFieldObject pfo) {
                            head.append(" = ").append(pfo.contents());
                        } else if (data.fieldObj() instanceof ErrorFieldObject) {
                            head.append(" error!");
                        } else if (data.fieldObj() instanceof ComplexFieldObject cfo) {
                            String text = cfo.text();

                            if (shortenNames)
                                text = text.substring(text.lastIndexOf('.') + 1);

                            head.append(" ").append(text);
                        } else if (data.fieldObj() instanceof NbtCompoundFieldObject nfo) {
                            head.append(" ").append(nfo.data());
                        } else if (data.fieldObj() instanceof BytesFieldObject bfo) {
                            head.append(" ").append(bfo.text());
                        }

                        dumper.write(indent, head.toString());

                        if (data.fieldObj() instanceof ErrorFieldObject efo) {
                            dumper.writeLines(indent + 1, efo.fullExceptionText());
                        } else if (data.fieldObj() instanceof BytesFieldObject bfo) {
                            dumper.writeHexDump(indent + 1, bfo.data());
                        }

                        if (data.fieldObj() instanceof ComplexFieldObject && depthLeft > 0) {
                            return dumpToText(dumper, indent + 1, data, depthLeft - 1);
                        } else {
                            return CompletableFuture.completedFuture(null);
                        }
                    });
                }

                return runningTotal;
            });
    }

    public CompletableFuture<Void> dumpToJson(JsonWriter writer, N startingAt, int depthLeft,
                                              Consumer<ObjectPath> dumpingConsumer) {
        dumpingConsumer.accept(startingAt.path());
        return startingAt
            .ensureChildren()
            .thenCompose(children -> {
                try {
                    writer.beginObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                CompletableFuture<Void> runningTotal = CompletableFuture.completedFuture(null);

                for (var entry : children.entrySet()) {
                    runningTotal = runningTotal.thenCompose(ignored -> {
                        try {
                            var data = entry.getValue();

                            writer.name(entry.getKey().toString());
                            writer.beginObject();

                            writer.name("type");
                            writer.value(data.fieldObj().type());

                            writer.name("is_mixin");
                            writer.value(data.isMixin());

                            if (data.fieldObj() instanceof PrimitiveFieldObject pfo) {
                                writer.name("value");
                                writer.value(pfo.contents());
                            } else if (data.fieldObj() instanceof ErrorFieldObject efo) {
                                writer.name("class");
                                writer.value(efo.exceptionClass());

                                writer.name("full");
                                writer.value(efo.fullExceptionText());
                            } else if (data.fieldObj() instanceof ComplexFieldObject cfo) {
                                writer.name("class");
                                writer.value(MappingsManager.displayMappings().mapClass(cfo.className()));

                                writer.name("tag");
                                writer.value(cfo.tag());
                            } else if (data.fieldObj() instanceof NbtCompoundFieldObject nfo) {
                                writer.name("data");
                                writer.value(nfo.data().toString());
                            } else if (data.fieldObj() instanceof BytesFieldObject bfo) {
                                writer.name("buffer_class");
                                writer.value(MappingsManager.displayMappings().mapField(bfo.bufferClass()));

                                writer.name("bytes_len");
                                writer.value(bfo.data().length);

                                writer.name("bytes_b64");
                                writer.value(Base64.getEncoder().encodeToString(bfo.data()));
                            }

                            if (data.fieldObj() instanceof ComplexFieldObject cfo && !cfo.isRepeat() && depthLeft > 0) {
                                writer.name("fields");
                                return dumpToJson(writer, data, depthLeft - 1, dumpingConsumer)
                                    .thenRun(() -> {
                                        try {
                                            writer.endObject();
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                            } else {
                                writer.endObject();
                                return CompletableFuture.completedFuture(null);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }

                return runningTotal.thenRun(() -> {
                    try {
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
    }

    protected abstract N createNodeFrom(ObjectPath path, FieldData data);

    Map<PathStep, N> processMap(ObjectPath base, Map<PathStep, FieldData> originalChildren) {
        Map<PathStep, N> nodes = new LinkedHashMap<>();

        for (var entry : originalChildren.entrySet()) {
            if (entry.getKey() instanceof FieldPathStep field
             && Gadget.CONFIG.hiddenFields().contains(field.fieldId()))
                continue;

            nodes.put(entry.getKey(), createNodeFrom(base.then(entry.getKey()), entry.getValue()));
        }

        return nodes;
    }

    CompletableFuture<Map<PathStep, N>> requestFields(ObjectPath path, int from, int limit) {
        return source.requestFieldsOf(path, from, limit)
            .thenApply(children -> processMap(path, children));
    }
}
