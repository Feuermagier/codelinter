package de.firemage.autograder.api;

import fluent.bundle.FluentResource;
import fluent.syntax.parser.FTLParser;
import fluent.syntax.parser.FTLStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for building FluentResources from strings.
 */
public class FluentBuilder {
    private final List<String> lines = new ArrayList<>();

    public static FluentResource ofSingle(String id, String value) {
        return build(id + " = " + value);
    }

    public FluentBuilder message(String id, String value) {
        lines.add(id + " = " + value);
        return this;
    }

    public FluentResource build() {
        return build(String.join("\n", lines));
    }

    private static FluentResource build(String content) {
        if (content.isBlank()) {
            return new FluentResource(List.of(), List.of(), List.of());
        }

        var bundle = FTLParser.parse(FTLStream.of(content));
        if (bundle.hasErrors()) {
            throw new IllegalStateException("Could not parse the fluent resource: " + bundle.errors().toString());
        }
        return bundle;
    }
}
