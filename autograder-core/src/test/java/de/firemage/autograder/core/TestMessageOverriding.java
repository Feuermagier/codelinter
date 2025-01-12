package de.firemage.autograder.core;

import de.firemage.autograder.api.AbstractLinter;
import de.firemage.autograder.api.FluentBuilder;
import fluent.syntax.parser.FTLParser;
import fluent.syntax.parser.FTLStream;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMessageOverriding {
    @Test
    void testNoMessageOverride() {
        var builder = AbstractLinter.builder(Locale.ENGLISH);
        var linter = new Linter(builder);
        String msg = linter.translateMessage(new LocalizedMessage("status-compiling"));
        assertEquals("Compiling", msg);
    }

    @Test
    void testSingleMessageOverride() {
        String fluentFile = "status-compiling = Foo Bar";

        var builder =
                AbstractLinter.builder(Locale.ENGLISH).messagesOverride(FTLParser.parse(FTLStream.of(fluentFile)));
        var linter = new Linter(builder);
        String msg = linter.translateMessage(new LocalizedMessage("status-compiling"));
        assertEquals("Foo Bar", msg);
    }

    @Test
    void testDoubleMessageOverride() {
        String fluentFile1 = "status-compiling = param {$x}";
        String fluentFile2 = "status-compiling = Foo Bar";

        var builder = AbstractLinter.builder(Locale.ENGLISH)
                .messagesOverride(FTLParser.parse(FTLStream.of(fluentFile1)))
                .messagesOverride(FTLParser.parse(FTLStream.of(fluentFile2)));
        var linter = new Linter(builder);
        String msg = linter.translateMessage(new LocalizedMessage("status-compiling", Map.of("x", "xyz")));
        assertEquals("param xyz", msg);
    }

    @Test
    void testNonOverriddenMessageWithOverrides() {
        String fluentFile = "status-compiling = Foo Bar";

        var builder =
                AbstractLinter.builder(Locale.ENGLISH).messagesOverride(FTLParser.parse(FTLStream.of(fluentFile)));
        var linter = new Linter(builder);
        String msg = linter.translateMessage(new LocalizedMessage("status-model"));
        assertEquals("Building the code model", msg);
    }

    @Test
    void testConditionalMessageOverride() {
        var builder = AbstractLinter.builder(Locale.ENGLISH)
                .conditionalOverride(ProblemType.AVOID_LABELS, FluentBuilder.ofSingle("avoid-labels", "Foo Bar"));
        var linter = new Linter(builder);
        String msg = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-labels"), ProblemType.AVOID_LABELS));
        assertEquals("Foo Bar", msg);
    }

    @Test
    void testMultipleConditionalMessageOverrides() {
        var builder = AbstractLinter.builder(Locale.ENGLISH)
                .conditionalOverride(ProblemType.AVOID_SHADOWING, FluentBuilder.ofSingle("avoid-shadowing", "1234"))
                .conditionalOverride(ProblemType.AVOID_LABELS, FluentBuilder.ofSingle("avoid-labels", "Foo Bar"));
        var linter = new Linter(builder);

        String msg = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-labels"), ProblemType.AVOID_LABELS));
        assertEquals("Foo Bar", msg);

        String msg2 = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-shadowing"), ProblemType.AVOID_SHADOWING));
        assertEquals("1234", msg2);
    }

    @Test
    void testMultipleConditionalMessageOverridesForSameProblemType() {
        var builder = AbstractLinter.builder(Locale.ENGLISH)
                .conditionalOverride(ProblemType.AVOID_SHADOWING, FluentBuilder.ofSingle("avoid-shadowing", "1234"))
                .conditionalOverride(ProblemType.AVOID_SHADOWING, FluentBuilder.ofSingle("avoid-labels", "Foo Bar"));
        var linter = new Linter(builder);

        String msg = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-labels"), ProblemType.AVOID_SHADOWING));
        assertEquals("Foo Bar", msg);

        String msg2 = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-shadowing"), ProblemType.AVOID_SHADOWING));
        assertEquals("1234", msg2);
    }

    @Test
    void testConditionalMessageOverrideForSameProblemType() {
        var builder = AbstractLinter.builder(Locale.ENGLISH)
                .conditionalOverride(ProblemType.AVOID_SHADOWING, FluentBuilder.ofSingle("avoid-shadowing", "1234"));
        var linter = new Linter(builder);

        String msg = linter.translateMessage(new LocalizedMessageForProblem(new LocalizedMessage("avoid-labels"), ProblemType.AVOID_SHADOWING));
        assertEquals("Labels should be avoided.", msg);

    }
}
