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
package edu.ohsu.cslu.grammar;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import edu.ohsu.cslu.util.MutableEnumeration;

/**
 * Stores a grammar in compressed-sparse-column (CSC) format
 */
public abstract class CscSparseMatrixGrammar extends SparseMatrixGrammar {

    private static final long serialVersionUID = 1L;

    /**
     * Parallel array with {@link #cscBinaryPopulatedColumnOffsets}, containing indices of populated columns (child
     * pairs) and the offsets into {@link #cscBinaryRowIndices} at which each column starts. Array size is the number of
     * non-empty columns (+1 to simplify loops).
     */
    public final int[] cscBinaryPopulatedColumns;

    /**
     * Parallel array with {@link #cscBinaryPopulatedColumns} containing offsets into {@link #cscBinaryRowIndices} at
     * which each column starts.
     */
    public final int[] cscBinaryPopulatedColumnOffsets;

    /**
     * Offsets into {@link #cscBinaryRowIndices} and {@link #cscBinaryProbabilities} of the first entry for each column;
     * indexed by column number. Array size is the number of columns (+1 to simplify loops).
     */
    public final int[] cscBinaryColumnOffsets;

    /**
     * Row indices of each matrix entry in {@link #cscBinaryProbabilities}. One entry for each binary rule; the same
     * size as {@link #cscBinaryProbabilities}.
     */
    public final short[] cscBinaryRowIndices;

    /**
     * Binary rule probabilities One entry for each binary rule; the same size as {@link #cscBinaryRowIndices} .
     */
    public final float[] cscBinaryProbabilities;

    /**
     * Parallel array with {@link #cscBinaryPopulatedColumnOffsets}, containing indices of populated columns (child
     * pairs) and the offsets into {@link #cscBinaryRowIndices} at which each column starts. Array size is the number of
     * non-empty columns (+1 to simplify loops).
     */
    public final int[] factoredCscBinaryPopulatedColumns;

    /**
     * Parallel array with {@link #cscBinaryPopulatedColumns} containing offsets into {@link #cscBinaryRowIndices} at
     * which each column starts.
     */
    public final int[] factoredCscBinaryPopulatedColumnOffsets;

    /**
     * Offsets into {@link #factoredCscBinaryRowIndices} and {@link #factoredCscBinaryProbabilities} of the first entry
     * for each column; indexed by column number. Array size is the number of columns (+1 to simplify loops).
     */
    public final int[] factoredCscBinaryColumnOffsets;

    /**
     * Row indices of each matrix entry in {@link #cscBinaryProbabilities}. One entry for each binary rule; the same
     * size as {@link #cscBinaryProbabilities}.
     */
    public final short[] factoredCscBinaryRowIndices;

    /**
     * Binary rule probabilities One entry for each binary rule; the same size as {@link #cscBinaryRowIndices} .
     */
    public final float[] factoredCscBinaryProbabilities;

    protected CscSparseMatrixGrammar(final Reader grammarFile, final TokenClassifier tokenClassifier,
            final Class<? extends PackingFunction> cartesianProductFunctionClass) throws IOException {
        super(grammarFile, tokenClassifier, cartesianProductFunctionClass);

        // All binary productions
        final int[] populatedBinaryColumnIndices = populatedBinaryColumnIndices(tmpBinaryProductions, packingFunction);
        cscBinaryPopulatedColumns = new int[populatedBinaryColumnIndices.length];
        cscBinaryPopulatedColumnOffsets = new int[cscBinaryPopulatedColumns.length + 1];
        cscBinaryRowIndices = new short[tmpBinaryProductions.size()];
        cscBinaryProbabilities = new float[tmpBinaryProductions.size()];
        cscBinaryColumnOffsets = new int[packingFunction.packedArraySize()];
        factoredCscBinaryColumnOffsets = new int[packingFunction.packedArraySize()];

        storeRulesAsMatrix(tmpBinaryProductions, packingFunction, populatedBinaryColumnIndices,
                cscBinaryPopulatedColumns, cscBinaryPopulatedColumnOffsets, cscBinaryColumnOffsets,
                cscBinaryRowIndices, cscBinaryProbabilities);

        // TODO De-duplicate; move into storeRulesAsMatrix?
        for (int i = 0; i < cscBinaryPopulatedColumns.length; i++) {
            cscBinaryColumnOffsets[cscBinaryPopulatedColumns[i]] = cscBinaryPopulatedColumnOffsets[i];
        }
        for (int i = cscBinaryColumnOffsets.length - 1, lastOffset = cscBinaryPopulatedColumnOffsets[cscBinaryPopulatedColumnOffsets.length - 1]; i > cscBinaryPopulatedColumns[0]; i--) {
            if (cscBinaryColumnOffsets[i] == 0) {
                cscBinaryColumnOffsets[i] = lastOffset;
            } else {
                lastOffset = cscBinaryColumnOffsets[i];
            }
        }

        // Factored productions only
        final Collection<Production> factoredBinaryProductions = getFactoredBinaryProductions();
        final int[] factoredPopulatedBinaryColumnIndices = populatedBinaryColumnIndices(factoredBinaryProductions,
                packingFunction);
        factoredCscBinaryPopulatedColumns = new int[factoredPopulatedBinaryColumnIndices.length];
        factoredCscBinaryPopulatedColumnOffsets = new int[factoredCscBinaryPopulatedColumns.length + 1];
        factoredCscBinaryRowIndices = new short[factoredBinaryProductions.size()];
        factoredCscBinaryProbabilities = new float[factoredBinaryProductions.size()];

        storeRulesAsMatrix(factoredBinaryProductions, packingFunction, factoredPopulatedBinaryColumnIndices,
                factoredCscBinaryPopulatedColumns, factoredCscBinaryPopulatedColumnOffsets,
                factoredCscBinaryColumnOffsets, factoredCscBinaryRowIndices, factoredCscBinaryProbabilities);

        // Allow temporary binary production array to be GC'd
        tmpBinaryProductions = null;
    }

    public CscSparseMatrixGrammar(final ArrayList<Production> binaryProductions,
            final ArrayList<Production> unaryProductions, final ArrayList<Production> lexicalProductions,
            final MutableEnumeration<String> vocabulary, final MutableEnumeration<String> lexicon, final GrammarFormatType grammarFormat,
            final TokenClassifier tokenClassifier, final Class<? extends PackingFunction> functionClass,
            final boolean initCscMatrices) {

        super(binaryProductions, unaryProductions, lexicalProductions, vocabulary, lexicon, grammarFormat,
                tokenClassifier, functionClass);

        // Initialization code duplicated from constructor above to allow these fields to be final
        final int[] populatedBinaryColumnIndices = populatedBinaryColumnIndices(binaryProductions, packingFunction);
        cscBinaryPopulatedColumns = new int[populatedBinaryColumnIndices.length];
        cscBinaryPopulatedColumnOffsets = new int[cscBinaryPopulatedColumns.length + 1];
        cscBinaryRowIndices = new short[binaryProductions.size()];
        cscBinaryProbabilities = new float[binaryProductions.size()];
        cscBinaryColumnOffsets = new int[packingFunction.packedArraySize()];

        storeRulesAsMatrix(binaryProductions, packingFunction, populatedBinaryColumnIndices, cscBinaryPopulatedColumns,
                cscBinaryPopulatedColumnOffsets, cscBinaryColumnOffsets, cscBinaryRowIndices, cscBinaryProbabilities);

        // Factored productions only
        final Collection<Production> factoredBinaryProductions = getFactoredBinaryProductions();
        final int[] factoredPopulatedBinaryColumnIndices = populatedBinaryColumnIndices(factoredBinaryProductions,
                packingFunction);
        factoredCscBinaryPopulatedColumns = new int[factoredPopulatedBinaryColumnIndices.length];
        factoredCscBinaryPopulatedColumnOffsets = new int[factoredCscBinaryPopulatedColumns.length + 1];
        factoredCscBinaryRowIndices = new short[factoredBinaryProductions.size()];
        factoredCscBinaryProbabilities = new float[factoredBinaryProductions.size()];
        factoredCscBinaryColumnOffsets = new int[packingFunction.packedArraySize()];

        storeRulesAsMatrix(factoredBinaryProductions, packingFunction, factoredPopulatedBinaryColumnIndices,
                factoredCscBinaryPopulatedColumns, factoredCscBinaryPopulatedColumnOffsets,
                factoredCscBinaryColumnOffsets, factoredCscBinaryRowIndices, factoredCscBinaryProbabilities);
    }

    protected int[] populatedBinaryColumnIndices(final Collection<Production> productions, final PackingFunction pf) {
        final IntSet populatedBinaryColumnIndices = new IntOpenHashSet(productions.size() / 10);
        for (final Production p : productions) {
            populatedBinaryColumnIndices.add(pf.pack((short) p.leftChild, (short) p.rightChild));
        }
        final int[] sortedPopulatedBinaryColumnIndices = populatedBinaryColumnIndices.toIntArray();
        Arrays.sort(sortedPopulatedBinaryColumnIndices);
        return sortedPopulatedBinaryColumnIndices;
    }

    /**
     * Stores binary rules in Compressed-Sparse-Column (CSC) matrix format.
     * 
     * @param productions
     * @param pf TODO
     * @param validPackedChildPairs Sorted array of valid child pairs
     * @param cscPopulatedColumns
     * @param cscPopulatedColumnOffsets
     * @param cscColumnOffsets
     * @param cscRowIndices
     * @param cscProbabilities
     */
    protected void storeRulesAsMatrix(final Collection<Production> productions, final PackingFunction pf,
            final int[] validPackedChildPairs, final int[] cscPopulatedColumns, final int[] cscPopulatedColumnOffsets,
            final int[] cscColumnOffsets, final short[] cscRowIndices, final float[] cscProbabilities) {

        if (productions.size() == 0) {
            return;
        }

        // Bin all rules by child pair, mapping parent -> probability
        final Int2ObjectOpenHashMap<Int2FloatOpenHashMap> maps = new Int2ObjectOpenHashMap<Int2FloatOpenHashMap>(1000);
        final IntSet populatedColumnSet = new IntOpenHashSet(productions.size() / 8);

        for (final Production p : productions) {
            final int childPair = pf.pack((short) p.leftChild, (short) p.rightChild);
            populatedColumnSet.add(childPair);
            Int2FloatOpenHashMap map1 = maps.get(childPair);
            if (map1 == null) {
                map1 = new Int2FloatOpenHashMap(20);
                maps.put(childPair, map1);
            }
            map1.put(p.parent, p.prob);
        }

        // Store rules in CSC matrix
        final int[] populatedColumns = populatedColumnSet.toIntArray();
        Arrays.sort(populatedColumns);
        int j = 0;
        for (int i = 0; i < populatedColumns.length; i++) {
            final int childPair = populatedColumns[i];

            cscPopulatedColumns[i] = childPair;
            cscPopulatedColumnOffsets[i] = j;
            cscColumnOffsets[childPair] = j;

            final Int2FloatOpenHashMap map = maps.get(childPair);
            final int[] parents = map.keySet().toIntArray();
            Arrays.sort(parents);

            for (int k = 0; k < parents.length; k++) {
                cscRowIndices[j] = (short) parents[k];
                cscProbabilities[j++] = map.get(parents[k]);
            }
        }
        cscPopulatedColumnOffsets[cscPopulatedColumnOffsets.length - 1] = j;

        for (int i = cscColumnOffsets.length - 1, lastOffset = j; i > populatedColumns[0]; i--) {
            if (cscColumnOffsets[i] == 0) {
                cscColumnOffsets[i] = lastOffset;
            } else {
                lastOffset = cscColumnOffsets[i];
            }
        }

    }

    @Override
    public final float binaryLogProbability(final int parent, final int childPair) {

        // Find the column (child pair)
        final int c = cscBinaryColumnOffsets[childPair];
        if (c < 0) {
            return Float.NEGATIVE_INFINITY;
        }

        for (int i = cscBinaryPopulatedColumnOffsets[c]; i < cscBinaryPopulatedColumnOffsets[c + 1]; i++) {
            final int row = cscBinaryRowIndices[i];
            if (row == parent) {
                return cscBinaryProbabilities[i];
            }
            if (row > parent) {
                return Float.NEGATIVE_INFINITY;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    @Override
    public int numBinaryProds() {
        return cscBinaryProbabilities.length;
    }

    @Override
    public ArrayList<Production> getBinaryProductions() {
        final ArrayList<Production> binaryProductions = new ArrayList<Production>(cscUnaryProbabilities.length);

        for (int childPair = 0; childPair < cscBinaryColumnOffsets.length - 1; childPair++) {
            final short leftChild = (short) packingFunction.unpackLeftChild(childPair);
            final short rightChild = packingFunction.unpackRightChild(childPair);

            for (int i = cscBinaryColumnOffsets[childPair]; i < cscBinaryColumnOffsets[childPair + 1]; i++) {
                binaryProductions.add(new Production(cscBinaryRowIndices[i], leftChild, rightChild,
                        cscBinaryProbabilities[i], this));
            }
        }
        return binaryProductions;
    }
}
