package edu.ohsu.cslu.parser;

import com.aliasi.util.Collections;

import edu.ohsu.cslu.grammar.GrammarByChild;
import edu.ohsu.cslu.grammar.Grammar.Production;
import edu.ohsu.cslu.parser.CellChart.ChartCell;

public class ECPGrammarLoop extends CellwiseExhaustiveChartParser<GrammarByChild, CellChart> {

    public ECPGrammarLoop(final ParserOptions opts, final GrammarByChild grammar) {
        super(opts, grammar);
    }

    @Override
    protected void visitCell(final ChartCell cell) {
        final int start = cell.start(), end = cell.end();

        for (int mid = start + 1; mid <= end - 1; mid++) { // mid point
            // naive traversal through all grammar rules
            final ChartCell leftCell = chart.getCell(start, mid);
            final ChartCell rightCell = chart.getCell(mid, end);
            for (final Production p : grammar.getBinaryProductions()) {
                final float leftInside = leftCell.getInside(p.leftChild);
                final float rightInside = rightCell.getInside(p.rightChild);
                final float prob = p.prob + leftInside + rightInside;
                if (prob > Float.NEGATIVE_INFINITY) {
                    cell.updateInside(p, leftCell, rightCell, prob);
                }
            }
        }

        for (final int childNT : Collections.toIntArray(cell.getNTs())) {
            for (final Production p : grammar.getUnaryProductionsWithChild(childNT)) {
                cell.updateInside(chart.new ChartEdge(p, cell));
            }
        }
    }
}
