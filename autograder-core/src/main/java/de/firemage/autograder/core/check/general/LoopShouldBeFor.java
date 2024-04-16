package de.firemage.autograder.core.check.general;

import de.firemage.autograder.core.LocalizedMessage;
import de.firemage.autograder.core.ProblemType;
import de.firemage.autograder.core.check.ExecutableCheck;
import de.firemage.autograder.core.dynamic.DynamicAnalysis;
import de.firemage.autograder.core.integrated.IntegratedCheck;
import de.firemage.autograder.core.integrated.SpoonUtil;
import de.firemage.autograder.core.integrated.StaticAnalysis;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtWhile;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@ExecutableCheck(reportedProblems = {ProblemType.LOOP_SHOULD_BE_FOR})
public class LoopShouldBeFor extends IntegratedCheck {
    private static CtFor createCtFor(
        Collection<? extends CtStatement> init,
        CtExpression<Boolean> condition,
        Collection<? extends CtStatement> forUpdate,
        CtStatement body
    ) {
        CtFor ctFor = body.getFactory().Core().createFor();
        ctFor.setForInit(init.stream().map(CtStatement::clone).toList());
        if (condition != null) {
            ctFor.setExpression(condition.clone());
        }
        ctFor.setForUpdate(forUpdate.stream().map(CtStatement::clone).toList());
        ctFor.setBody(body.clone());
        return ctFor;
    }

    private record LoopSuggestion(CtStatement beforeLoop, CtFor ctFor) {
        @Override
        public String toString() {
            String result = "%n%s".formatted(this.ctFor);

            if (this.beforeLoop != null) {
                result = "%n%s%s".formatted(this.beforeLoop, result);
            }

            return result;
        }
    }

    private static LoopSuggestion getCounter(CtLoop ctLoop) {
        List<CtStatement> statements = SpoonUtil.getEffectiveStatements(ctLoop.getBody());

        if (statements.isEmpty()) {
            return null;
        }


        CtStatement previous = SpoonUtil.getPreviousStatement(ctLoop).orElse(null);
        if (!(previous instanceof CtLocalVariable<?> ctLocalVariable) || !SpoonUtil.isPrimitiveNumeric(ctLocalVariable.getType())) {
            return null;
        }

        CtStatement lastStatement = null;
        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement statement = statements.get(i);

            // ensure that the last statement is an assignment to the loop variable
            if ((statement instanceof CtAssignment<?, ?> ctAssignment
                && ctAssignment.getAssigned() instanceof CtVariableWrite<?> ctVariableWrite
                && ctVariableWrite.getVariable().equals(ctLocalVariable.getReference()))
                || (statement instanceof CtUnaryOperator<?> ctUnaryOperator
                && ctUnaryOperator.getOperand() instanceof CtVariableWrite<?> ctWrite
                && ctWrite.getVariable().equals(ctLocalVariable.getReference()))) {
                lastStatement = statement;
                break;
            }

            // the control variable is used after the update statement or there is no update statement
            if (SpoonUtil.hasAnyUsesIn(ctLocalVariable, statement)) {
                return null;
            }
        }

        // could not find an update statement => ignoring loop suggestion
        if (lastStatement == null) {
            return null;
        }

        // ignore loops where the control variable is updated more than once in the body
        CtStatement finalLastStatement = lastStatement;
        boolean isUpdatedMultipleTimes = statements.stream()
            .filter(statement -> statement != finalLastStatement)
            .anyMatch(
                statement -> SpoonUtil.hasAnyUsesIn(ctLocalVariable, statement, ctElement -> ctElement instanceof CtVariableWrite<?>));

        if (isUpdatedMultipleTimes) {
            return null;
        }


        // we now know that there is some primitive local variable in front of the loop and
        // that the last statement updates it.
        //
        // the condition for stopping is the loop condition
        CtStatement newBody;
        if (ctLoop.getBody() instanceof CtBlock<?> block) {
            CtBlock<?> newBlock = block.clone();

            // remove the update statement from the loop body:
            newBlock.removeStatement(lastStatement);
            newBody = newBlock;
        } else {
            // there is only one statement in the loop body (the update statement)
            // => set an empty block as body
            newBody = ctLoop.getFactory().createBlock();
        }

        boolean isUsedAfterLoop = SpoonUtil.getNextStatements(ctLoop)
            .stream()
            .anyMatch(statement -> SpoonUtil.hasAnyUsesIn(ctLocalVariable, statement));

        var init = List.of(ctLocalVariable);
        if (isUsedAfterLoop) {
            init = List.of();
        }

        CtExpression<Boolean> condition = null;
        if (ctLoop instanceof CtWhile ctWhile) {
            if (!(ctWhile.getLoopingExpression() instanceof CtLiteral<Boolean> literal) || !(literal.getValue().equals(true))) {
                condition = ctWhile.getLoopingExpression();
            }
        } else if (ctLoop instanceof CtForEach ctForEach) {
            // ignore loops where the variable is used in the loop body
            if (SpoonUtil.hasAnyUsesIn(ctForEach.getVariable(), ctForEach.getBody())) {
                return null;
            }

            Factory factory = ctLoop.getFactory();
            CtExpression<?> upperBound;

            if (ctForEach.getExpression().getType().isArray()) {
                CtFieldReference<?> arrayLengthReference = factory.createFieldReference();
                arrayLengthReference.setDeclaringType(ctForEach.getExpression().getType().clone());
                arrayLengthReference.setSimpleName("length");
                CtFieldRead fieldRead = factory.createFieldRead();
                fieldRead.setTarget(ctForEach.getExpression().clone());
                fieldRead.setVariable(arrayLengthReference);
                upperBound = fieldRead;
            } else {
                var methods = ctForEach.getExpression().getType().getTypeDeclaration().getMethodsByName("size");
                if (methods.isEmpty()) {
                    return null;
                }

                upperBound = factory.createInvocation(ctForEach.getExpression().clone(), methods.get(0).getReference(), List.of());
            }

            condition = SpoonUtil.createBinaryOperator(
                factory.createVariableRead(ctLocalVariable.getReference(), false),
                upperBound,
                BinaryOperatorKind.LT
            );
        } else {
            return null;
        }

        CtFor ctFor = createCtFor(
            init,
            condition,
            List.of(lastStatement),
            newBody
        );

        if (isUsedAfterLoop) {
            return new LoopSuggestion(ctLocalVariable, ctFor);
        }

        return new LoopSuggestion(null, ctFor);
    }

    @Override
    protected void check(StaticAnalysis staticAnalysis, DynamicAnalysis dynamicAnalysis) {
        staticAnalysis.processWith(new AbstractProcessor<CtLoop>() {
            @Override
            public void process(CtLoop ctLoop) {
                if (ctLoop.isImplicit() || !ctLoop.getPosition().isValidPosition() || ctLoop.getBody() == null) {
                    return;
                }

                LoopSuggestion forLoop = getCounter(ctLoop);

                if (forLoop != null) {
                    addLocalProblem(
                        ctLoop,
                        new LocalizedMessage(
                            "loop-should-be-for",
                            Map.of(
                                "suggestion", forLoop.toString()
                            )
                        ),
                        ProblemType.LOOP_SHOULD_BE_FOR
                    );
                }
            }
        });
    }
}
