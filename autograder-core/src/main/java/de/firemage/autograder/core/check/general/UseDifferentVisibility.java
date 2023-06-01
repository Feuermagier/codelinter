package de.firemage.autograder.core.check.general;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import de.firemage.autograder.core.LocalizedMessage;
import de.firemage.autograder.core.ProblemType;
import de.firemage.autograder.core.check.ExecutableCheck;
import de.firemage.autograder.core.dynamic.DynamicAnalysis;
import de.firemage.autograder.core.integrated.IntegratedCheck;
import de.firemage.autograder.core.integrated.StaticAnalysis;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.DirectReferenceFilter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@ExecutableCheck(reportedProblems = { ProblemType.USE_DIFFERENT_VISIBILITY })
public class UseDifferentVisibility extends IntegratedCheck {
    // returns all parents of a CtElement
    private static Iterable<CtElement> parents(CtElement ctElement) {
        return () -> new Iterator<>() {
            private CtElement current = ctElement;

            @Override
            public boolean hasNext() {
                return this.current.isParentInitialized();
            }

            @Override
            public CtElement next() throws NoSuchElementException {
                if (!this.hasNext()) {
                    throw new NoSuchElementException("No more parents");
                }

                CtElement result = this.current.getParent();
                this.current = result;
                return result;
            }
        };
    }

    private static <T> Iterable<T> chain(Iterable<? extends T> first, Iterable<? extends T> second) {
        return () -> Iterators.concat(first.iterator(), second.iterator());
    }

    private static CtElement findCommonParent(CtElement firstElement, Iterable<? extends CtElement> others) {
        // CtElement::hasParent will recursively call itself until it reaches the root
        // => inefficient and might cause a stack overflow

        Set<CtElement> ctParents = new LinkedHashSet<>();
        ctParents.add(firstElement);
        parents(firstElement).forEach(ctParents::add);

        for (CtElement other : others) {
            ctParents.retainAll(Sets.newHashSet(parents(other).iterator()));
        }

        return ctParents.iterator().next();
    }

    private enum Visibility implements Comparable<Visibility> {
        PRIVATE,
        DEFAULT,
        PROTECTED,
        PUBLIC;

        static Visibility of(CtModifiable ctModifiable) {
            if (ctModifiable.isPublic()) {
                return PUBLIC;
            } else if (ctModifiable.isProtected()) {
                return PROTECTED;
            } else if (ctModifiable.isPrivate()) {
                return PRIVATE;
            } else {
                return DEFAULT;
            }
        }

        boolean isMoreRestrictiveThan(Visibility other) {
            // this < other
            return this.compareTo(other) < 0;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private static Visibility getVisibility(CtTypeMember ctTypeMember, CtReference ctReference) {
        CtModel ctModel = ctReference.getFactory().getModel();

        List<CtReference> references = ctModel.getElements(new DirectReferenceFilter<>(ctReference));

        CtElement commonParent = findCommonParent(ctTypeMember, references);
        CtType<?> declaringType = ctTypeMember.getDeclaringType();

        if (ctTypeMember == commonParent) {
            return Visibility.PRIVATE;
        }

        if (commonParent instanceof CtPackage ctPackage && ctPackage.equals(ctModel.getRootPackage())) {
            if (ctTypeMember.getParent(CtPackage.class).equals(ctPackage)
                && references.stream().allMatch(ref -> ref.getParent(CtPackage.class).equals(ctPackage))) {
                return Visibility.DEFAULT;
            }

            return Visibility.PUBLIC;
        }

        // if all they share a common parent type, and it is declared there as well, it should be private
        if (commonParent instanceof CtType<?> ctType
            && (ctType.equals(declaringType)
            // special case for inner classes
            || ctTypeMember.getTopLevelType().equals(ctType))) {
            return Visibility.PRIVATE;
        }

        return Visibility.of(ctTypeMember);
    }

    @Override
    protected void check(StaticAnalysis staticAnalysis, DynamicAnalysis dynamicAnalysis) {
        staticAnalysis.getModel().getRootPackage().accept(new CtScanner() {
            @Override
            public <T> void visitCtField(CtField<T> ctField) {
                if (!ctField.getPosition().isValidPosition()
                    || ctField.isImplicit()
                    || ctField.isPrivate()) {
                    super.visitCtField(ctField);
                    return;
                }

                Visibility currentVisibility = Visibility.of(ctField);
                Visibility visibility = getVisibility(ctField, ctField.getType());
                if (visibility.isMoreRestrictiveThan(currentVisibility)) {
                    addLocalProblem(
                        ctField,
                        new LocalizedMessage(
                            "use-different-visibility",
                            Map.of(
                                "name", ctField.getSimpleName(),
                                "suggestion", visibility.toString()
                            )
                        ),
                        ProblemType.USE_DIFFERENT_VISIBILITY
                    );
                }

                super.visitCtField(ctField);
            }
        });
    }
}