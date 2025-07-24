// Taken from https://github.com/wisp-forest/owo-lib/blob/1.19/src/main/java/io/wispforest/owo/config/ui/ConfigScreen.java.
// Includes modifications.

package io.wispforest.gadget.client.gui.search;

import blue.endless.jankson.annotation.Nullable;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SearchGui extends FlowLayout {
    private final ScrollContainer<?> scroll;

    protected @Nullable SearchMatches currentMatches = null;
    private int currentMatchIndex;
    private final TextBoxComponent searchBox;

    public SearchGui(ScrollContainer<?> scroll) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);
        this.scroll = scroll;

        var searchRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());

        searchBox = Components.textBox(Sizing.fill(50));
        searchBox.setDrawsBackground(false);
        searchBox.verticalSizing(Sizing.fixed(9));
        LabelComponent matchIndicator = Components.label(Text.empty());
        matchIndicator.margins(Insets.horizontal(5));

        searchRow
            .child(searchBox)
            .child(matchIndicator)
            .surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(3));

        this
            .child(Components.texture(
                    Identifier.of("owo", "textures/gui/config_search.png"),
                    0,
                    0,
                    16,
                    16,
                    16,
                    16)
                .margins(Insets.of(2)))
            .child(searchRow);

        var searchHint = I18n.translate("text.owo.config.search");
        searchBox.setSuggestion(searchHint);
        searchBox.onChanged().subscribe(s -> {
            searchBox.setSuggestion(s.isEmpty() ? searchHint : "");

            searchBox.setEditableColor(TextFieldWidget.DEFAULT_EDITABLE_COLOR);
            matchIndicator.text(Text.empty());
        });

        searchBox.keyPress().subscribe((keyCode, scanCode, modifiers) -> {
            if (keyCode != GLFW.GLFW_KEY_ENTER) return false;

            var query = searchBox.getText().toLowerCase(Locale.ROOT);
            if (query.isBlank()) return false;

            if (this.currentMatches != null && this.currentMatches.query().equals(query)) {
                if (this.currentMatches.matches().isEmpty()) {
                    this.currentMatchIndex = -1;
                } else {
                    this.currentMatchIndex = (this.currentMatchIndex + 1) % this.currentMatches.matches().size();
                }
            } else {
                var splitQuery = query.split(" ");

                this.currentMatchIndex = 0;
                this.currentMatches = new SearchMatches(query, this.collectSearchAnchors()
                    .stream()
                    .filter(anchor -> Arrays.stream(splitQuery).allMatch(anchor.currentSearchText()::contains))
                    .toList());
            }

            if (this.currentMatches.matches().isEmpty()) {
                matchIndicator.text(Text.translatable("text.owo.config.search.no_matches"));
                searchBox.setEditableColor(0xEB1D36);
            } else {
                matchIndicator.text(Text.translatable("text.owo.config.search.matches", this.currentMatchIndex + 1, this.currentMatches.matches().size()));
                searchBox.setEditableColor(0x28FFBF);

                var selectedMatch = this.currentMatches.matches().get(this.currentMatchIndex);
                var anchorFrame = selectedMatch.anchorFrame();

                if (anchorFrame instanceof FlowLayout flow) {
                    flow.child(0, new SearchHighlighterComponent());
                }

                if (anchorFrame.y() < scroll.y() || anchorFrame.y() + anchorFrame.height() > scroll.y() + scroll.height()) {
                    scroll.scrollTo(selectedMatch.anchorFrame());
                }
            }

            return true;
        });
    }

    public TextBoxComponent searchBox() {
        return searchBox;
    }

    protected List<SearchAnchorComponent> collectSearchAnchors() {
        var discovered = new ArrayList<SearchAnchorComponent>();
        var candidates = new ArrayDeque<>(scroll.children());

        while (!candidates.isEmpty()) {
            var candidate = candidates.poll();
            if (candidate instanceof ParentComponent parentComponent) {
                candidates.addAll(parentComponent.children());
            } else if (candidate instanceof SearchAnchorComponent anchor) {
                discovered.add(anchor);
            }
        }

        return discovered;
    }
}
