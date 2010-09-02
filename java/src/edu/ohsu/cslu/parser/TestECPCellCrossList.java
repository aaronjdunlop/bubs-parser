package edu.ohsu.cslu.parser;

import org.junit.Test;

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.LeftListGrammar;
import edu.ohsu.cslu.tests.PerformanceTest;

/**
 * Unit and performance tests for {@link TestECPCellCrossList}
 * 
 * @author Aaron Dunlop
 * @since Dec 23, 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class TestECPCellCrossList extends ExhaustiveChartParserTestCase<ECPCellCrossList> {

    @Override
    protected Class<? extends Grammar> grammarClass() {
        return LeftListGrammar.class;
    }

    @Override
    @Test
    @PerformanceTest({ "mbp", "17959", "d820", "21563" })
    public void profileSentences11Through20() throws Exception {
        internalProfileSentences11Through20();

    }
}
