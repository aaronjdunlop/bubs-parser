package edu.ohsu.cslu.parser.chart;

import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.grammar.Grammar.Production;
import edu.ohsu.cslu.parser.Parser;

public class DenseVectorChart extends CellChart {

    protected DenseVectorChart(final DenseVectorChartCell[][] chart, final boolean viterbiMax,
            final Parser<?> parser) {
        this.size = chart.length;
        this.viterbiMax = true;
        this.parser = parser;
        this.chart = chart;
    }

    public DenseVectorChart(final int size, final boolean viterbiMax, final Parser<?> parser) {
        this.size = size;
        this.viterbiMax = true;
        this.parser = parser;

        chart = new HashSetChartCell[size][size + 1];
        for (int start = 0; start < size; start++) {
            for (int end = start + 1; end < size + 1; end++) {
                chart[start][end] = new DenseVectorChartCell(start, end);
            }
        }
    }

    /**
     * Type-strengthen return-type. For the moment, we have to do it by casting, but better here than
     * everywhere.
     */
    @Override
    public DenseVectorChartCell getCell(final int start, final int end) {
        return (DenseVectorChartCell) chart[start][end];
    }

    public class DenseVectorChartCell extends HashSetChartCell {

        public final SparseMatrixGrammar sparseMatrixGrammar;

        /** Indexed by parent non-terminal */
        public final short[] midpoints;
        public final int[] children;

        /** Stores packed children and their inside probabilities */
        public int numValidLeftChildren;
        public int[] validLeftChildren;
        public float[] validLeftChildrenProbabilities;

        public int numValidRightChildren;
        public short[] validRightChildren;
        public float[] validRightChildrenProbabilities;

        public DenseVectorChartCell(final int start, final int end) {
            super(start, end);
            this.sparseMatrixGrammar = (SparseMatrixGrammar) parser.grammar;

            final int arraySize = sparseMatrixGrammar.numNonTerms();
            this.midpoints = new short[arraySize];
            this.children = new int[arraySize];
        }

        @Override
        public void finalizeCell() {

            validLeftChildren = new int[numValidLeftChildren];
            validLeftChildrenProbabilities = new float[numValidLeftChildren];
            validRightChildren = new short[numValidRightChildren];
            validRightChildrenProbabilities = new float[numValidRightChildren];

            int leftIndex = 0, rightIndex = 0;

            for (int nonterminal = 0; nonterminal < sparseMatrixGrammar.numNonTerms(); nonterminal++) {
                final float probability = inside[nonterminal];

                if (probability != Float.NEGATIVE_INFINITY) {

                    if (sparseMatrixGrammar.isValidLeftChild(nonterminal)) {
                        validLeftChildren[leftIndex] = nonterminal;
                        validLeftChildrenProbabilities[leftIndex++] = probability;
                    }
                    if (sparseMatrixGrammar.isValidRightChild(nonterminal)) {
                        validRightChildren[rightIndex] = (short) nonterminal;
                        validRightChildrenProbabilities[rightIndex++] = probability;
                    }
                }
            }
        }

        @Override
        public void updateInside(final Chart.ChartEdge edge) {
            updateInside(edge.prod, edge.leftCell, edge.rightCell, edge.inside());
        }

        @Override
        public void updateInside(final Production p, final ChartCell leftCell, final ChartCell rightCell,
                final float insideProb) {
            final int parent = p.parent;
            numEdgesConsidered++;

            if (inside[parent] == Float.NEGATIVE_INFINITY) {
                if (sparseMatrixGrammar.isValidLeftChild(parent)) {
                    numValidLeftChildren++;
                }
                if (sparseMatrixGrammar.isValidRightChild(parent)) {
                    numValidRightChildren++;
                }
            }

            // final float insideProb = p.prob + leftCell.getInside(p.leftChild) +
            // rightCell.getInside(p.rightChild);
            if (insideProb > inside[parent]) {

                // Midpoint == end for unary productions
                midpoints[parent] = (short) leftCell.end();
                inside[parent] = insideProb;
                children[parent] = sparseMatrixGrammar.cartesianProductFunction().pack(p.leftChild,
                    (short) p.rightChild);

                numEdgesAdded++;
            }
        }

        @Override
        public ChartEdge getBestEdge(final int nonTermIndex) {
            if (inside[nonTermIndex] == Float.NEGATIVE_INFINITY) {
                return null;
            }

            final int leftChild = sparseMatrixGrammar.cartesianProductFunction().unpackLeftChild(
                children[nonTermIndex]);
            final short rightChild = sparseMatrixGrammar.cartesianProductFunction().unpackRightChild(
                children[nonTermIndex]);

            final int midpoint = midpoints[nonTermIndex];

            final DenseVectorChartCell leftChildCell = getCell(start(), midpoint);
            final DenseVectorChartCell rightChildCell = midpoint < size() ? (DenseVectorChartCell) getCell(
                midpoint, end()) : null;

            Production p;
            if (rightChild == Production.LEXICAL_PRODUCTION) {
                final float probability = sparseMatrixGrammar.lexicalLogProbability(nonTermIndex, leftChild);
                p = sparseMatrixGrammar.new Production(nonTermIndex, leftChild, probability, true);

            } else if (rightChild == Production.UNARY_PRODUCTION) {
                final float probability = sparseMatrixGrammar.unaryLogProbability(nonTermIndex, leftChild);
                p = sparseMatrixGrammar.new Production(nonTermIndex, leftChild, probability, false);

            } else {
                final float probability = sparseMatrixGrammar.binaryLogProbability(nonTermIndex,
                    children[nonTermIndex]);
                p = sparseMatrixGrammar.new Production(nonTermIndex, leftChild, rightChild, probability);
            }
            return new ChartEdge(p, leftChildCell, rightChildCell);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(256);

            sb.append("SparseChartCell[" + start() + "][" + end() + "] with " + getNumNTs() + " (of "
                    + sparseMatrixGrammar.numNonTerms() + ") edges\n");

            for (int nonterminal = 0; nonterminal < sparseMatrixGrammar.numNonTerms(); nonterminal++) {
                if (inside[nonterminal] != Float.NEGATIVE_INFINITY) {
                    final int childProductions = children[nonterminal];
                    final float insideProbability = inside[nonterminal];
                    final int midpoint = midpoints[nonterminal];

                    final int leftChild = sparseMatrixGrammar.cartesianProductFunction().unpackLeftChild(
                        childProductions);
                    final short rightChild = sparseMatrixGrammar.cartesianProductFunction().unpackRightChild(
                        childProductions);

                    if (rightChild == Production.UNARY_PRODUCTION) {
                        // Unary Production
                        sb.append(String.format("%s -> %s (%.5f, %d)\n", sparseMatrixGrammar
                            .mapNonterminal(nonterminal), sparseMatrixGrammar.mapNonterminal(leftChild),
                            insideProbability, midpoint));
                    } else if (rightChild == Production.LEXICAL_PRODUCTION) {
                        // Lexical Production
                        sb.append(String.format("%s -> %s (%.5f, %d)\n", sparseMatrixGrammar
                            .mapNonterminal(nonterminal), sparseMatrixGrammar.mapLexicalEntry(leftChild),
                            insideProbability, midpoint));
                    } else {
                        sb.append(String.format("%s -> %s %s (%.5f, %d)\n", sparseMatrixGrammar
                            .mapNonterminal(nonterminal), sparseMatrixGrammar.mapNonterminal(leftChild),
                            sparseMatrixGrammar.mapNonterminal(rightChild), insideProbability, midpoint));
                    }
                }
            }
            return sb.toString();
        }

        @Override
        public int getNumNTs() {
            int entries = 0;
            for (int nonterminal = 0; nonterminal < sparseMatrixGrammar.numNonTerms(); nonterminal++) {
                if (inside[nonterminal] != Float.NEGATIVE_INFINITY) {
                    entries++;
                }
            }
            return entries;
        }
    }
}
