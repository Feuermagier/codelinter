package de.firemage.autograder.core;

import de.firemage.autograder.api.AbstractProblemType;
import de.firemage.autograder.api.AbstractTranslations;
import fluent.bundle.FluentBundle;
import fluent.bundle.FluentResource;
import fluent.functions.icu.ICUFunctionFactory;
import fluent.syntax.parser.FTLParser;
import fluent.syntax.parser.FTLStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Translations implements AbstractTranslations {
    private final FluentBundle mainTranslations;
    private final Map<AbstractProblemType, FluentBundle> conditionalTranslations;

    public Translations(Locale locale, List<FluentResource> mainOverrides, Map<AbstractProblemType, List<FluentResource>> conditionalOverrides) {
        String filename = switch (locale.getLanguage()) {
            case "de" -> "/strings.de.ftl";
            case "en" -> "/strings.en.ftl";
            default -> throw new IllegalArgumentException("No translation available for the locale " + locale);
        };

        try {
            // == Normal messages
            var fluentBuilder = FluentBundle.builder(locale, ICUFunctionFactory.INSTANCE);

            // The name FluentBuilder#addResourceOverriding is a lie; it does not override preexisting messages

            // message overrides
            for (var bundle : mainOverrides) {
                fluentBuilder.addResourceOverriding(bundle);
            }

            // default messages
            try (var stream = this.getClass().getResourceAsStream(filename)) {
                if (stream == null) {
                    throw new IllegalStateException("Could not find the autograder messages");
                }
                fluentBuilder.addResourceOverriding(FTLParser.parse(FTLStream.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8))));
            }

            this.mainTranslations = fluentBuilder.build();

            // == Conditional messages
            this.conditionalTranslations = new HashMap<>();
            for (var entry : conditionalOverrides.entrySet()) {
                var conditionalBuilder = FluentBundle.builder(locale, ICUFunctionFactory.INSTANCE);
                for (var resource : entry.getValue()) {
                    conditionalBuilder.addResourceOverriding(resource);
                }
                this.conditionalTranslations.put(entry.getKey(), conditionalBuilder.build());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public FluentBundle getMainTranslations() {
        return this.mainTranslations;
    }

    @Override
    public FluentBundle getConditionalTranslations(AbstractProblemType problemType) {
        return this.conditionalTranslations.get(problemType);
    }
}
