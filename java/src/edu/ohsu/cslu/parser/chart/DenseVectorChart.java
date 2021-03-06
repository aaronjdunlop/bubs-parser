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
package edu.ohsu.cslu.parser.chart;

import java.util.Arrays;

import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PackingFunction;
import edu.ohsu.cslu.grammar.Vocabulary;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.chart.PackedArrayChart.TemporaryChartCell;
import edu.ohsu.cslu.util.MutableEnumeration;

/**
 * Stores a chart in a 3-way parallel array indexed by non-terminal:
 * <ol>
 * <li>Probabilities of each non-terminal (float; NEGATIVE_INFINITY for unobserved non-terminals)</li>
 * <li>Child pairs producing each non-terminal --- equivalent to the grammar rule (int)</li>
 * <li>Midpoints (short)</li>
 * </ol>
 * 
 * Those 3 pieces of information allow us to back-trace through the chart and construct the parse tree.
 * 
 * Each parallel array entry consumes 4 + 4 + 2 = 10 bytes
 * 
 * Individual cells in the parallel array are indexed by cell offsets of fixed length (the number of non-terminals in
 * the grammar).
 * 
 * The ancillary data structures are relatively small, so the total size consumed is approximately = n * (n-1) / 2 * V *
 * 10 bytes.
 * 
 * Similar to {@link PackedArrayChart}, but the observed non-terminals in a cell are not `packed' together at the
 * beginning of the cell's array range. This saves a packing scan in {@link DenseVectorChartCell#finalizeCell()}, but
 * results in less efficient access when populating subsequent cells, especially if the chart population is pruned
 * significantly.
 * 
 * @see PackedArrayChart
 * @author Aaron Dunlop
 * @since March 15, 2010
 */
public class DenseVectorChart extends ParallelArrayChart {

    /**
     * Constructs a chart
     * 
     * @param parseTask Input state
     * @param sparseMatrixGrammar Grammar
     */
    public DenseVectorChart(final ParseTask parseTask, final SparseMatrixGrammar sparseMatrixGrammar) {
        super(parseTask, sparseMatrixGrammar);
        Arrays.fill(insideProbabilities, Float.NEGATIVE_INFINITY);
    }

    @Override
    public void reset(final ParseTask task) {
        this.parseTask = task;
        this.size = task.sentenceLength();
        Arrays.fill(insideProbabilities, Float.NEGATIVE_INFINITY);
    }

    @Override
    public BinaryTree<String> extractBestParse(final int start, final int end, final int parent) {
        return extractBestParse(start, end, parent, sparseMatrixGrammar.nonTermSet, sparseMatrixGrammar.lexSet,
                sparseMatrixGrammar.packingFunction);
    }

    public BinaryTree<String> extractBestParse(final int start, final int end, final int parent,
            final Vocabulary vocabulary, final MutableEnumeration<String> lexicon, final PackingFunction packingFunction) {

        final DenseVectorChartCell packedCell = getCell(start, end);

        if (packedCell == null) {
            return null;
        }

        // Find the index of the non-terminal in the chart storage
        final int parentIndex = packedCell.offset + parent;
        if (insideProbabilities[parentIndex] == Float.NEGATIVE_INFINITY) {
            return null;
        }
        final int edgeChildren = packedChildren[parentIndex];
        final short edgeMidpoint = midpoints[parentIndex];

        final BinaryTree<String> subtree = new BinaryTree<String>(vocabulary.getSymbol(parent));
        final int leftChild = packingFunction.unpackLeftChild(edgeChildren);
        final int rightChild = packingFunction.unpackRightChild(edgeChildren);

        if (rightChild == Production.UNARY_PRODUCTION) {
            subtree.addChild(extractBestParse(start, end, leftChild));

        } else if (rightChild == Production.LEXICAL_PRODUCTION) {
            subtree.addChild(new BinaryTree<String>(lexicon.getSymbol(leftChild)));

        } else {
            // binary production
            subtree.addChild(extractBestParse(start, edgeMidpoint, leftChild));
            subtree.addChild(extractBestParse(edgeMidpoint, end, rightChild));
        }
        return subtree;
    }

    @Override
    public float getInside(final int start, final int end, final int nonTerminal) {
        final int cellIndex = cellIndex(start, end);
        final int offset = cellIndex * sparseMatrixGrammar.numNonTerms();
        return insideProbabilities[offset + nonTerminal];
    }

    @Override
    public void updateInside(final int start, final int end, final int nonTerminal, final float insideProb) {
        throw new UnsupportedOperationException("Not supported by PackedArrayChart");
    }

    @Override
    public DenseVectorChartCell getCell(final int start, final int end) {
        return new DenseVectorChartCell(start, end);
    }

    public class DenseVectorChartCell extends ParallelArrayChartCell {

        public DenseVectorChartCell(final int start, final int end) {
            super(start, end);
        }

        @Override
        public float getInside(final int nonTerminal) {
            return insideProbabilities[offset + nonTerminal];
        }

        @Override
        public void updateInside(final Production p, final ChartCell leftCell, final ChartCell rightCell,
                final float insideProbability) {

            final int index = offset + p.parent;
            numEdgesConsidered++;

            if (insideProbability > insideProbabilities[index]) {
                if (p.isBinaryProd()) {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction().pack((short) p.leftChild,
                            (short) p.rightChild);
                } else if (p.isLexProd()) {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction().packLexical(p.leftChild);
                } else {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction().packUnary((short) p.leftChild);
                }
                insideProbabilities[index] = insideProbability;

                // Midpoint == end for unary productions
                midpoints[index] = leftCell.end();

                numEdgesAdded++;
            }
        }

        @Override
        public void updateInside(final ChartEdge edge) {

            final int index = offset + edge.prod.parent;
            numEdgesConsidered++;

            if (edge.inside() > insideProbabilities[index]) {

                if (edge.prod.isBinaryProd()) {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction().pack((short) edge.prod.leftChild,
                            (short) edge.prod.rightChild);
                } else if (edge.prod.isLexProd()) {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction().packLexical(edge.prod.leftChild);
                } else {
                    packedChildren[index] = sparseMatrixGrammar.packingFunction()
                            .packUnary((short) edge.prod.leftChild);
                }
                insideProbabilities[index] = edge.inside();

                // Midpoint == end for unary productions
                midpoints[index] = edge.leftCell.end();

                numEdgesAdded++;
            }
        }

        @Override
        public void storeLexicalProductions(final int child, final short[] parents, final float[] probabilities) {

            final int packedChild = sparseMatrixGrammar.packingFunction().packLexical(child);

            for (int i = 0; i < parents.length; i++) {
                final int index = offset + parents[i];
                insideProbabilities[index] = probabilities[i];
                packedChildren[index] = packedChild;
            }
        }

        @Override
        public ChartEdge getBestEdge(final int nonTerminal) {

            final int index = offset + nonTerminal;
            final int edgeChildren = packedChildren[index];
            final short edgeMidpoint = midpoints[index];

            final int leftChild = sparseMatrixGrammar.packingFunction().unpackLeftChild(edgeChildren);
            final int rightChild = sparseMatrixGrammar.packingFunction().unpackRightChild(edgeChildren);

            final DenseVectorChartCell leftChildCell = getCell(start(), edgeMidpoint);
            final DenseVectorChartCell rightChildCell = edgeMidpoint < end ? (DenseVectorChartCell) getCell(
                    edgeMidpoint, end) : null;

            Production p;
            if (rightChild == Production.LEXICAL_PRODUCTION) {
                final float probability = sparseMatrixGrammar.lexicalLogProbability((short) nonTerminal, leftChild);
                p = new Production(nonTerminal, leftChild, probability, true, sparseMatrixGrammar);

            } else if (rightChild == Production.UNARY_PRODUCTION) {
                final float probability = sparseMatrixGrammar.unaryLogProbability((short) nonTerminal,
                        (short) leftChild);
                p = new Production(nonTerminal, leftChild, probability, false, sparseMatrixGrammar);

            } else {
                final float probability = sparseMatrixGrammar.binaryLogProbability((short) nonTerminal, edgeChildren);
                p = new Production(nonTerminal, leftChild, rightChild, probability, sparseMatrixGrammar);
            }
            return new ChartEdge(p, leftChildCell, rightChildCell);
        }

        @Override
        public int getNumNTs() {
            int count = 0;
            for (int i = offset; i < (offset + sparseMatrixGrammar.numNonTerms()); i++) {
                if (insideProbabilities[i] != Float.NEGATIVE_INFINITY) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int getNumUnfactoredNTs() {
            int count = 0;
            for (short nt = 0; nt < sparseMatrixGrammar.numNonTerms(); nt++) {
                if (insideProbabilities[nt + offset] != Float.NEGATIVE_INFINITY && !grammar.nonTermSet.isFactored(nt)) {
                    count++;
                }
            }
            return count;
        }

        public void allocateTemporaryStorage() {
            // Allocate storage
            if (tmpCell == null) {
                // TODO Use the thread-local version. This will require deciding at thread-local init time whether to
                // allocate outside probability array
                // this.tmpCell = threadLocalTemporaryCells.get();
                // this.tmpCell.clear();

                this.tmpCell = new TemporaryChartCell(grammar, false);
                // Copy from main chart array to temporary parallel array
                System.arraycopy(insideProbabilities, offset, tmpCell.insideProbabilities, 0,
                        tmpCell.insideProbabilities.length);
                System.arraycopy(packedChildren, offset, tmpCell.packedChildren, 0, tmpCell.packedChildren.length);
                System.arraycopy(midpoints, offset, tmpCell.midpoints, 0, tmpCell.midpoints.length);
            }
        }

        @Override
        public final void finalizeCell() {
            if (tmpCell == null) {
                return;
            }

            System.arraycopy(tmpCell.insideProbabilities, 0, insideProbabilities, offset,
                    tmpCell.insideProbabilities.length);
            System.arraycopy(tmpCell.packedChildren, 0, packedChildren, offset, tmpCell.packedChildren.length);
            System.arraycopy(tmpCell.midpoints, 0, midpoints, offset, tmpCell.midpoints.length);
            // Note : outsideProbabilities is not implemented in DenseVectorChart
        }

        @Override
        public void finalizeCell(final short entryNonTerminal, final float entryInsideProbability,
                final int entryPackedChildren, final short entryMidpoint) {
            Arrays.fill(insideProbabilities, offset, offset + grammar.numNonTerms(), Float.NEGATIVE_INFINITY);
            insideProbabilities[offset + entryNonTerminal] = entryInsideProbability;
            packedChildren[offset + entryNonTerminal] = entryPackedChildren;
            midpoints[offset + entryNonTerminal] = entryMidpoint;
        }

        @Override
        public void finalizeEmptyCell() {
            // NOOP - chart storage should still be empty.
        }

        /**
         * Warning: Not truly thread-safe, since it doesn't validate that the two cells belong to the same chart.
         */
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof DenseVectorChartCell)) {
                return false;
            }

            final DenseVectorChartCell packedArrayChartCell = (DenseVectorChartCell) o;
            return (packedArrayChartCell.start == start && packedArrayChartCell.end == end);
        }

        @Override
        public String toString(final boolean formatFractions) {
            final StringBuilder sb = new StringBuilder(256);

            sb.append("DenseVectorChartCell[" + start() + "][" + end() + "] with " + getNumNTs() + " (of "
                    + sparseMatrixGrammar.numNonTerms() + ") edges\n");

            // Format entries from the main chart array
            for (int nonTerminal = 0; nonTerminal < sparseMatrixGrammar.numNonTerms(); nonTerminal++) {
                final int index = offset + nonTerminal;
                final float insideProbability = insideProbabilities[index];

                if (insideProbability > Float.NEGATIVE_INFINITY) {
                    final int childProductions = packedChildren[index];
                    final int midpoint = midpoints[index];
                    sb.append(formatCellEntry(sparseMatrixGrammar, index - offset, childProductions, insideProbability,
                            midpoint, formatFractions));
                }
            }
            return sb.toString();
        }
    }
}
