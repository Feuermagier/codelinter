package de.firemage.codelinter.core.check;

import de.firemage.codelinter.core.pmd.PMDCheck;
import net.sourceforge.pmd.lang.java.rule.bestpractices.AvoidReassigningParametersRule;

public class DontReassignParametersCheck extends PMDCheck {
    private static final String DESCRIPTION = "Don't reassign method/constructor parameters";

    public DontReassignParametersCheck() {
        super(DESCRIPTION, DESCRIPTION, new AvoidReassigningParametersRule());
    }
}
