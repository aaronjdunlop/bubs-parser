/*
 * Copyright 2010-2014, Oregon Health & Science University
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Further documentation and contact information is available at
 *   https://code.google.com/p/bubs-parser/ 
 */
package edu.ohsu.cslu.parser.ecp;

import edu.ohsu.cslu.grammar.LeftListGrammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.ChartParser;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell;
import edu.ohsu.cslu.parser.chart.Chart.ChartCell;

/**
 * Exhaustive chart parser which performs grammar intersection by iterating over grammar rules matching the observed
 * left child non-terminals.
 * 
 * @author Nathan Bodenstab
 */
public class ECPCellCrossList extends ChartParser<LeftListGrammar, CellChart> {

    private long numConsidered = 0;

    public ECPCellCrossList(final ParserDriver opts, final LeftListGrammar grammar) {
        super(opts, grammar);
    }

    @Override
    protected void initSentence(final ParseTask parseTask) {
        super.initSentence(parseTask);
        numConsidered = 0;
    }

    @Override
    protected void computeInsideProbabilities(final ChartCell c) {
        final long t0 = collectDetailedStatistics ? System.nanoTime() : 0;

        final HashSetChartCell cell = (HashSetChartCell) c;
        final short start = cell.start();
        final short end = cell.end();
        float leftInside, rightInside;

        final int midStart = cellSelector.getMidStart(start, end);
        final int midEnd = cellSelector.getMidEnd(start, end);
        final boolean onlyFactored = cellSelector.hasCellConstraints() && cellSelector.isCellOnlyFactored(start, end);

        for (int mid = midStart; mid <= midEnd; mid++) { // mid point
            final HashSetChartCell leftCell = chart.getCell(start, mid);
            final HashSetChartCell rightCell = chart.getCell(mid, end);
            for (final int leftNT : leftCell.getLeftChildNTs()) {
                leftInside = leftCell.getInside(leftNT);
                for (final Production p : grammar.getBinaryProductionsWithLeftChild(leftNT)) {
                    numConsidered++;
                    if (!onlyFactored || grammar.getOrAddNonterm((short) p.parent).isFactored()) {
                        rightInside = rightCell.getInside(p.rightChild);
                        if (rightInside > Float.NEGATIVE_INFINITY) {
                            cell.updateInside(p, leftCell, rightCell, p.prob + leftInside + rightInside);
                        }
                    }
                }
            }
        }

        if (collectDetailedStatistics) {
            chart.parseTask.insideBinaryNs += System.nanoTime() - t0;
        }

        if (cellSelector.hasCellConstraints() == false || cellSelector.isUnaryOpen(start, end)) {
            for (final int childNT : cell.getNtArray()) {
                for (final Production p : grammar.getUnaryProductionsWithChild(childNT)) {
                    numConsidered++;
                    cell.updateInside(p, p.prob + cell.getInside(childNT));
                }
            }
        }
    }

    @Override
    public String getStats() {
        return super.getStats() + "numConsidered=" + numConsidered;
    }
}
