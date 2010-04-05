package edu.ohsu.cslu.parser;

import java.util.Arrays;

import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.grammar.Grammar.Production;
import edu.ohsu.cslu.parser.chart.Chart;
import edu.ohsu.cslu.parser.chart.Chart.ChartCell;
import edu.ohsu.cslu.parser.chart.Chart.ChartEdge;
import edu.ohsu.cslu.parser.chart.DenseVectorChart.DenseVectorChartCell;

/**
 * A class of parser which performs the grammar intersection in each cell by:
 * <ol>
 * <li>Finding the cartesian product of possible child productions in child cells across all possible
 * midpoints.
 * <li>Multiplying that cartesian product vector by the grammar matrix (stored in a sparse format).
 * <ol>
 * 
 * Subclasses use a variety of sparse matrix grammar representations, and differ in how they perform the
 * cartesian product. Some implementations perform the vector and matrix operations on GPU hardware throgh
 * OpenCL.
 * 
 * @author Aaron Dunlop
 * @since Mar 26, 2010
 * 
 * @version $Revision$ $Date$ $Author$
 */
public abstract class SparseMatrixVectorParser<G extends SparseMatrixGrammar, C extends Chart> extends
        ExhaustiveChartParser<G, C> {

    protected float[] crossProductProbabilities;
    protected short[] crossProductMidpoints;

    public long startTime = 0;
    public long totalCartesianProductTime = 0;
    public long totalCartesianProductUnionTime = 0;
    public long totalSpMVTime = 0;

    public SparseMatrixVectorParser(final ParserOptions opts, final G grammar) {
        super(opts, grammar);
    }

    public SparseMatrixVectorParser(final G grammar) {
        super(grammar);
    }

    /**
     * Multiplies the grammar matrix (stored sparsely) by the supplied cross-product vector (stored densely),
     * and populates this chart cell.
     * 
     * @param crossProductVector
     * @param chartCell
     */
    public abstract void binarySpmvMultiply(final CrossProductVector crossProductVector,
            final ChartCell chartCell);

    /**
     * Multiplies the unary grammar matrix (stored sparsely) by the contents of this cell (stored densely),
     * and populates this chart cell. Used to populate unary rules.
     * 
     * @param chartCell
     */
    public abstract void unarySpmvMultiply(final ChartCell chartCell);

    @Override
    protected void initParser(final int sentLength) {
        startTime = System.currentTimeMillis();
        totalSpMVTime = 0;
        totalCartesianProductTime = 0;
        totalCartesianProductUnionTime = 0;
    }

    // TODO Do this with a matrix multiply?
    @Override
    protected void addLexicalProductions(final int[] sent) throws Exception {
        for (int i = 0; i < chart.size(); i++) {
            final ChartCell cell = chart.getCell(i, i + 1);
            for (final Production lexProd : grammar.getLexicalProductionsWithChild(sent[i])) {
                cell.updateInside(new ChartEdge(lexProd, cell));
            }
            cell.finalizeCell();
        }
    }

    /**
     * Takes the cross-product of all potential child-cell combinations. Unions those cross-products together,
     * saving the maximum probability child combinations.
     * 
     * @param start
     * @param end
     * @return Unioned cross-product
     */
    protected CrossProductVector crossProductUnion(final int start, final int end) {

        if (crossProductProbabilities == null) {
            crossProductProbabilities = new float[grammar.packedArraySize()];
            crossProductMidpoints = new short[grammar.packedArraySize()];
        }

        Arrays.fill(crossProductProbabilities, Float.NEGATIVE_INFINITY);
        int size = 0;

        // Iterate over all possible midpoints, unioning together the cross-product of discovered
        // non-terminals in each left/right child pair
        for (short midpoint = (short) (start + 1); midpoint <= end - 1; midpoint++) {
            final DenseVectorChartCell leftCell = (DenseVectorChartCell) chart.getCell(start, midpoint);
            final DenseVectorChartCell rightCell = (DenseVectorChartCell) chart.getCell(midpoint, end);

            final int[] leftChildren = leftCell.validLeftChildren;
            final float[] leftChildrenProbabilities = leftCell.validLeftChildrenProbabilities;
            final short[] rightChildren = rightCell.validRightChildren;
            final float[] rightChildrenProbabilities = rightCell.validRightChildrenProbabilities;

            for (int i = 0; i < leftChildren.length; i++) {

                final int leftChild = leftChildren[i];
                final float leftProbability = leftChildrenProbabilities[i];

                for (int j = 0; j < rightChildren.length; j++) {

                    final float jointProbability = leftProbability + rightChildrenProbabilities[j];
                    final int child = grammar.pack(leftChild, rightChildren[j]);
                    final float currentProbability = crossProductProbabilities[child];

                    if (jointProbability > currentProbability) {
                        crossProductProbabilities[child] = jointProbability;
                        crossProductMidpoints[child] = midpoint;

                        if (currentProbability == Float.NEGATIVE_INFINITY) {
                            size++;
                        }
                    }
                }
            }
        }

        return new CrossProductVector(grammar, crossProductProbabilities, crossProductMidpoints, size);
    }

    @Override
    public String getStatHeader() {
        return String.format("%8s, %10s, %8s, %8s", "Total", "X-product", "X-union", "SpMV");
    }

    @Override
    public String getStats() {
        final long totalTime = System.currentTimeMillis() - startTime;
        return String.format("%8.1f, %10d, %8d, %8d", totalTime / 1000f, totalCartesianProductTime,
            totalCartesianProductUnionTime, totalSpMVTime);
    }

    public final static class CrossProductVector {

        private final SparseMatrixGrammar grammar;
        final float[] probabilities;
        final short[] midpoints;
        private int size = 0;

        public CrossProductVector(final SparseMatrixGrammar grammar, final float[] probabilities,
                final short[] midpoints, final int size) {
            this.grammar = grammar;
            this.probabilities = probabilities;
            this.midpoints = midpoints;
            this.size = size;
        }

        public final int size() {
            return size;
        }

        public final float probability(final int children) {
            return probabilities[children];
        }

        public final short midpoint(final int children) {
            return midpoints[children];
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(256);
            for (int i = 0; i < probabilities.length; i++) {
                if (probabilities[i] != Float.NEGATIVE_INFINITY) {
                    final int leftChild = grammar.unpackLeftChild(i);
                    final short rightChild = grammar.unpackRightChild(i);
                    final int midpoint = midpoints[i];
                    final float probability = probabilities[i];

                    if (rightChild == Production.UNARY_PRODUCTION) {
                        // Unary production
                        sb.append(String.format("%s (%d) %.3f (%d)\n", grammar.mapNonterminal(leftChild),
                            leftChild, probability, midpoint));

                    } else if (rightChild == Production.LEXICAL_PRODUCTION) {
                        // Lexical production
                        sb.append(String.format("%s (%d) %.3f (%d)\n", grammar.mapLexicalEntry(leftChild),
                            leftChild, probability, midpoint));

                    } else {
                        // Binary production
                        sb.append(String.format("%s (%d),%s (%d) %.3f (%d)\n", grammar
                            .mapNonterminal(leftChild), leftChild, grammar.mapNonterminal(rightChild),
                            rightChild, probability, midpoint));
                    }
                }
            }
            return sb.toString();
        }
    }
}
