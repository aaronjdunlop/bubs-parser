package edu.ohsu.cslu.ella;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2FloatOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.regex.Pattern;

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.grammar.SymbolSet;

/**
 * Stores a grammar as lists of rules. This representation is not efficient for parsing, but is quite effective and
 * intuitive for splitting and re-merging of states during latent-variable grammar learning.
 * 
 * @author Aaron Dunlop
 * @since Jan 13, 2011
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class ProductionListGrammar {

    protected final SplitVocabulary vocabulary;
    protected final SymbolSet<String> lexicon;

    protected final ArrayList<Production> binaryProductions;
    protected final ArrayList<Production> unaryProductions;
    protected final ArrayList<Production> lexicalProductions;

    private final String startSymbol;

    private final ProductionListGrammar parentGrammar;
    private final Int2IntMap parentVocabularyMap;

    private final static Pattern SUBSTATE_PATTERN = Pattern.compile("^.*_[0-9]+$");

    /**
     * Constructs a production-list grammar based on a {@link StringCountGrammar}, inducing a vocabulary sorted by
     * binary parent count.
     * 
     * @param countGrammar
     */
    public ProductionListGrammar(final StringCountGrammar countGrammar) {

        this.vocabulary = countGrammar.induceVocabulary(countGrammar.binaryParentCountComparator());
        this.lexicon = countGrammar.induceLexicon();

        this.binaryProductions = countGrammar.binaryProductions(vocabulary);
        this.unaryProductions = countGrammar.unaryProductions(vocabulary);
        this.lexicalProductions = countGrammar.lexicalProductions(vocabulary, lexicon);

        this.startSymbol = countGrammar.startSymbol;
        this.parentGrammar = null;
        this.parentVocabularyMap = null;
        //
        // // This grammar was induced from a treebank and has not (yet) been split or merged, so each non-terminal is
        // its
        // // own base category.
        // this.subcategoryIndices = new short[vocabulary.size()];
        // Arrays.fill(subcategoryIndices, (short) 0);
    }

    /**
     * Constructs a production-list grammar based on a {@link MappedCountGrammar}.
     * 
     * @param countGrammar
     */
    public ProductionListGrammar(final MappedCountGrammar countGrammar) {

        this.vocabulary = countGrammar.vocabulary;
        this.lexicon = countGrammar.lexicon;

        this.binaryProductions = countGrammar.binaryProductions();
        this.unaryProductions = countGrammar.unaryProductions();
        this.lexicalProductions = countGrammar.lexicalProductions();

        this.startSymbol = countGrammar.startSymbol;
        // TODO Record a parent grammar?
        this.parentGrammar = null;
        this.parentVocabularyMap = null;

        // // TODO Populate this, somehow
        // this.subcategoryIndices = new short[vocabulary.size()];
    }

    private ProductionListGrammar(final ProductionListGrammar parentGrammar, final SplitVocabulary vocabulary,
            final SymbolSet<String> lexicon) {
        this.vocabulary = vocabulary;
        this.lexicon = lexicon;

        this.binaryProductions = new ArrayList<Production>();
        this.unaryProductions = new ArrayList<Production>();
        this.lexicalProductions = new ArrayList<Production>();

        this.startSymbol = parentGrammar.startSymbol;
        this.parentGrammar = parentGrammar;
        this.parentVocabularyMap = new Int2IntOpenHashMap();
    }

    /**
     * Returns the log probability of a binary rule.
     * 
     * @param parent
     * @param leftChild
     * @param rightChild
     * @return Log probability of the specified rule.
     */
    public final float binaryLogProbability(final String parent, final String leftChild, final String rightChild) {

        final int intParent = vocabulary.getIndex(parent);
        final int intLeftChild = vocabulary.getIndex(leftChild);
        final int intRightChild = vocabulary.getIndex(rightChild);

        if (intParent < 0 || intLeftChild < 0 || intRightChild < 0) {
            return Float.NEGATIVE_INFINITY;
        }

        for (final Production p : binaryProductions) {
            if (p.parent == intParent && p.leftChild == intLeftChild && p.rightChild == intRightChild) {
                return p.prob;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    /**
     * Returns the log probability of a unary rule.
     * 
     * @param parent
     * @param child
     * @return Log probability of the specified rule.
     */
    public final float unaryLogProbability(final String parent, final String child) {

        final int intParent = vocabulary.getIndex(parent);
        final int intChild = vocabulary.getIndex(child);

        if (intParent < 0 || intChild < 0) {
            return Float.NEGATIVE_INFINITY;
        }

        for (final Production p : unaryProductions) {
            if (p.parent == intParent && p.leftChild == intChild) {
                return p.prob;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    /**
     * Returns the log probability of a lexical rule.
     * 
     * @param parent
     * @param child
     * @return Log probability of the specified rule.
     */
    public final float lexicalLogProbability(final String parent, final String child) {

        final int intParent = vocabulary.getIndex(parent);
        final int intChild = lexicon.getIndex(child);

        if (intParent < 0 || intChild < 0) {
            return Float.NEGATIVE_INFINITY;
        }

        for (final Production p : lexicalProductions) {
            if (p.parent == intParent && p.leftChild == intChild) {
                return p.prob;
            }
        }
        return Float.NEGATIVE_INFINITY;
    }

    /**
     * @return A string representation of the binary and unary rules, in the same format used by {@link Grammar}
     */
    public String pcfgString() {
        final StringBuilder sb = new StringBuilder();

        // Handle start symbol separately
        sb.append(startSymbol);
        sb.append('\n');

        for (final Production p : binaryProductions) {
            sb.append(String.format("%s -> %s %s %.6f\n", vocabulary.getSymbol(p.parent),
                    vocabulary.getSymbol(p.leftChild), vocabulary.getSymbol(p.rightChild), p.prob));
        }

        for (final Production p : unaryProductions) {
            sb.append(String.format("%s -> %s %.6f\n", vocabulary.getSymbol(p.parent),
                    vocabulary.getSymbol(p.leftChild), p.prob));
        }

        return sb.toString();
    }

    /**
     * @return A string representation of the lexicon, in the same format used by {@link Grammar}
     */
    public String lexiconString() {
        final StringBuilder sb = new StringBuilder();

        for (final Production p : lexicalProductions) {
            sb.append(String.format("%s -> %s %.6f\n", vocabulary.getSymbol(p.parent), lexicon.getSymbol(p.leftChild),
                    p.prob));
        }

        return sb.toString();
    }

    /**
     * Splits each non-terminal in the grammar into 2 sub-states, constructing a new grammar, vocabulary, and lexicon.
     * 
     * @param random Source of random noise
     * @param randomness Amount of randomness (0-1) to add to the rule probabilities in the new grammar. With 0 noise,
     *            the probabilities of each rule will be split equally. Some noise is generally required to break ties
     *            in the new grammar.
     * @return Newly-constructed grammar
     */
    public ProductionListGrammar split(final Random random, final float randomness) {
        // Produce a new vocabulary, splitting each non-terminal into two substates

        final SplitVocabulary splitVocabulary = new SplitVocabulary((short) (vocabulary.maxSplits * 2));

        // Do not split the start symbol
        splitVocabulary.addSymbol(startSymbol);
        for (int i = 1; i < vocabulary.size(); i++) {
            final String[] substates = substates(vocabulary.getSymbol(i));
            splitVocabulary.addSymbol(substates[0]);
            splitVocabulary.addSymbol(substates[1]);
        }
        splitVocabulary.recomputeSubcategoryIndices();

        final ProductionListGrammar splitGrammar = new ProductionListGrammar(this, splitVocabulary, lexicon);

        // Iterate through each rule, creating split rules in the new grammar

        // TODO Add random noise

        final float logOneEighth = (float) Math.log(.125);
        final float logOneFourth = (float) Math.log(.25);
        final float logOneHalf = (float) Math.log(.5);

        // Split binary productions in 8ths
        for (final Production p : binaryProductions) {

            final int[] splitParents = new int[] { p.parent * 2 - 1, p.parent * 2 };
            final int[] splitLeftChildren = new int[] { p.leftChild * 2 - 1, p.leftChild * 2 };
            final int[] splitRightChildren = new int[] { p.rightChild * 2 - 1, p.rightChild * 2 };

            for (final int splitParent : splitParents) {
                for (final int splitLeftChild : splitLeftChildren) {
                    for (final int splitRightChild : splitRightChildren) {
                        splitGrammar.binaryProductions.add(new Production(splitParent, splitLeftChild, splitRightChild,
                                p.prob + logOneEighth, splitVocabulary, lexicon));
                    }
                }
            }
        }

        // Split unary productions in 4ths
        for (final Production p : unaryProductions) {

            final int[] splitChildren = new int[] { p.leftChild * 2 - 1, p.leftChild * 2 };

            // Since we do not split the start symbol, we only split unaries of which it is the parent in two
            if (p.parent == 0) {
                for (final int splitChild : splitChildren) {
                    splitGrammar.unaryProductions.add(new Production(0, splitChild, p.prob + logOneHalf, false,
                            splitVocabulary, lexicon));
                }

            } else {
                final int[] splitParents = new int[] { p.parent * 2 - 1, p.parent * 2 };

                for (final int splitParent : splitParents) {
                    for (final int splitChild : splitChildren) {
                        splitGrammar.unaryProductions.add(new Production(splitParent, splitChild,
                                p.prob + logOneFourth, false, splitVocabulary, lexicon));
                    }
                }
            }
        }

        // Split lexical productions in half
        for (final Production p : lexicalProductions) {
            splitGrammar.lexicalProductions.add(new Production(p.parent * 2 - 1, p.child(), p.prob + logOneHalf, true,
                    splitVocabulary, lexicon));
            splitGrammar.lexicalProductions.add(new Production(p.parent * 2, p.child(), p.prob + logOneHalf, true,
                    splitVocabulary, lexicon));
        }

        return splitGrammar;
    }

    private String[] substates(final String state) {
        if (SUBSTATE_PATTERN.matcher(state).matches()) {
            final String[] rootAndIndex = state.split("_");
            final int substateIndex = Integer.parseInt(rootAndIndex[1]);
            return new String[] { rootAndIndex[0] + '_' + (substateIndex * 2),
                    rootAndIndex[0] + '_' + (substateIndex * 2 + 1) };
        }
        return new String[] { state + "_0", state + "_1" };
    }

    public String superState(final String substate) {
        if (parentGrammar == null) {
            return null;
        }

        return parentGrammar.vocabulary.getSymbol(parentVocabularyMap.get(vocabulary.getIndex(substate)));
    }

    /**
     * Re-merges splits specified by non-terminal indices, producing a new {@link ProductionListGrammar} with its own
     * vocabulary and lexicon.
     * 
     * @param indices Non-terminal indices to merge. Each index is assumed to be the <i>second</i> of a split pair.
     *            i.e., if A and B were split, merging A into B is equivalent to merging B into A. The merge operation
     *            assumes that the indices will be of each non-terminal B.
     * @return Merged grammar
     */
    public ProductionListGrammar merge(final short[] indices) {

        // TODO Map from split indices -> merged indices?

        // Create merged vocabulary and map from old vocab indices to new
        final Short2ShortOpenHashMap mergedIndices = new Short2ShortOpenHashMap();
        short j = 0;
        Arrays.sort(indices);
        final ArrayList<String> mergedSymbols = new ArrayList<String>();
        mergedSymbols.add(startSymbol);

        String previousRoot = "";
        int nextIndex = 0;

        for (short i = 1; i < vocabulary.size(); i++) {
            if (j < indices.length && indices[j] == i) {
                j++;
            } else {
                // This would be much shorter and clearer if Java had tuples...
                final String mergedRoot = vocabulary.getSymbol(i).split("_")[0];

                if (mergedRoot.equals(previousRoot)) {
                    // Add the next split index in order, which may not match that of the split grammar symbol
                    mergedSymbols.add(previousRoot + '_' + nextIndex);
                    nextIndex++;
                } else {
                    mergedSymbols.add(mergedRoot + "_0");
                    nextIndex = 1;
                }
                previousRoot = mergedRoot;
            }
            mergedIndices.put(i, (short) (mergedSymbols.size() - 1));
        }

        final SplitVocabulary mergedVocabulary = new SplitVocabulary(mergedSymbols);

        // Create maps to store new rules in
        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2FloatOpenHashMap>> binaryRuleMap = new Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2FloatOpenHashMap>>();
        final Short2ObjectOpenHashMap<Short2FloatOpenHashMap> unaryRuleMap = new Short2ObjectOpenHashMap<Short2FloatOpenHashMap>();
        final Short2ObjectOpenHashMap<Int2FloatOpenHashMap> lexicalRuleMap = new Short2ObjectOpenHashMap<Int2FloatOpenHashMap>();

        // For each existing rule, compute the new (merged) non-terminals and add the rule probability to the rule map.
        // If multiple split rules merge into a single merged rule, sum the probabilities.

        for (final Production p : binaryProductions) {
            addBinaryRuleProbability(binaryRuleMap, mergedIndices.get((short) p.parent),
                    mergedIndices.get((short) p.leftChild), mergedIndices.get((short) p.rightChild), p.prob);
        }

        for (final Production p : unaryProductions) {
            addUnaryRuleProbability(unaryRuleMap, mergedIndices.get((short) p.parent),
                    mergedIndices.get((short) p.leftChild), p.prob);
        }

        for (final Production p : lexicalProductions) {
            addLexicalRuleProbability(lexicalRuleMap, mergedIndices.get((short) p.parent), p.leftChild, p.prob);
        }

        final ProductionListGrammar mergedGrammar = new ProductionListGrammar(parentGrammar, mergedVocabulary, lexicon);
        mergedGrammar.addToBinaryProductionsList(binaryRuleMap);
        mergedGrammar.addToUnaryProductionsList(unaryRuleMap);
        mergedGrammar.addToLexicalProductionList(lexicalRuleMap);
        return mergedGrammar;
    }

    private void addBinaryRuleProbability(
            final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2FloatOpenHashMap>> binaryRuleMap,
            final short parent, final short leftChild, final short rightChild, final float probability) {

        Short2ObjectOpenHashMap<Short2FloatOpenHashMap> leftChildMap = binaryRuleMap.get(parent);
        if (leftChildMap == null) {
            leftChildMap = new Short2ObjectOpenHashMap<Short2FloatOpenHashMap>();
            binaryRuleMap.put(parent, leftChildMap);
        }

        Short2FloatOpenHashMap rightChildMap = leftChildMap.get(leftChild);
        if (rightChildMap == null) {
            rightChildMap = new Short2FloatOpenHashMap();
            rightChildMap.defaultReturnValue(Float.NEGATIVE_INFINITY);
            leftChildMap.put(leftChild, rightChildMap);
        }

        if (rightChildMap.containsKey(rightChild)) {
            rightChildMap.put(rightChild, edu.ohsu.cslu.util.Math.logSum(rightChildMap.get(rightChild), probability));
        } else {
            rightChildMap.put(rightChild, probability);
        }
    }

    private void addToBinaryProductionsList(
            final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2FloatOpenHashMap>> binaryRuleMap) {

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            if (!binaryRuleMap.containsKey(parent)) {
                continue;
            }

            final Short2ObjectOpenHashMap<Short2FloatOpenHashMap> leftChildMap = binaryRuleMap.get(parent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                if (!leftChildMap.containsKey(leftChild)) {
                    continue;
                }

                final Short2FloatOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    if (!rightChildMap.containsKey(rightChild)) {
                        continue;
                    }

                    binaryProductions.add(new Production(parent, leftChild, rightChild, rightChildMap.get(rightChild),
                            vocabulary, lexicon));
                }
            }
        }
    }

    private void addUnaryRuleProbability(final Short2ObjectOpenHashMap<Short2FloatOpenHashMap> unaryRuleMap,
            final short parent, final short child, final float probability) {

        Short2FloatOpenHashMap childMap = unaryRuleMap.get(parent);
        if (childMap == null) {
            childMap = new Short2FloatOpenHashMap();
            childMap.defaultReturnValue(Float.NEGATIVE_INFINITY);
            unaryRuleMap.put(parent, childMap);
        }

        if (childMap.containsKey(child)) {
            childMap.put(child, edu.ohsu.cslu.util.Math.logSum(childMap.get(child), probability));
        } else {
            childMap.put(child, probability);
        }
    }

    private void addToUnaryProductionsList(final Short2ObjectOpenHashMap<Short2FloatOpenHashMap> unaryRuleMap) {

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            if (!unaryRuleMap.containsKey(parent)) {
                continue;
            }

            final Short2FloatOpenHashMap childMap = unaryRuleMap.get(parent);

            for (short child = 0; child < vocabulary.size(); child++) {
                if (!childMap.containsKey(child)) {
                    continue;
                }

                unaryProductions.add(new Production(parent, child, childMap.get(child), false, vocabulary, lexicon));
            }
        }
    }

    private void addLexicalRuleProbability(final Short2ObjectOpenHashMap<Int2FloatOpenHashMap> lexicalRuleMap,
            final short parent, final int child, final float probability) {

        Int2FloatOpenHashMap childMap = lexicalRuleMap.get(parent);
        if (childMap == null) {
            childMap = new Int2FloatOpenHashMap();
            childMap.defaultReturnValue(Float.NEGATIVE_INFINITY);
            lexicalRuleMap.put(parent, childMap);
        }

        if (childMap.containsKey(child)) {
            childMap.put(child, edu.ohsu.cslu.util.Math.logSum(childMap.get(child), probability));
        } else {
            childMap.put(child, probability);
        }
    }

    private void addToLexicalProductionList(final Short2ObjectOpenHashMap<Int2FloatOpenHashMap> lexicalRuleMap) {

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            if (!lexicalRuleMap.containsKey(parent)) {
                continue;
            }

            final Int2FloatOpenHashMap childMap = lexicalRuleMap.get(parent);

            for (int child = 0; child < lexicon.size(); child++) {
                if (!childMap.containsKey(child)) {
                    continue;
                }

                lexicalProductions.add(new Production(parent, child, childMap.get(child), true, vocabulary, lexicon));
            }
        }
    }
}