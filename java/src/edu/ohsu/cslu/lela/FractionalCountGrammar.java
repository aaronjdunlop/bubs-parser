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
package edu.ohsu.cslu.lela;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ShortOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import edu.ohsu.cslu.grammar.DecisionTreeTokenClassifier;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.Language;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PackingFunction;
import edu.ohsu.cslu.grammar.Vocabulary;
import edu.ohsu.cslu.util.MutableEnumeration;
import edu.ohsu.cslu.util.Strings;

public class FractionalCountGrammar implements CountGrammar, Cloneable {

    public final Vocabulary vocabulary;
    public final MutableEnumeration<String> lexicon;
    protected final String startSymbol;

    /** Parent -> Left child -> Right child -> count */
    private final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2DoubleOpenHashMap>> binaryRuleCounts = new Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<Short2DoubleOpenHashMap>>();

    /** Parent -> child -> count */
    private final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> unaryRuleCounts = new Short2ObjectOpenHashMap<Short2DoubleOpenHashMap>();

    /** Parent -> child -> count. Tallies all lexical rule observations */
    private final Short2ObjectOpenHashMap<Int2DoubleOpenHashMap> lexicalRuleCounts = new Short2ObjectOpenHashMap<Int2DoubleOpenHashMap>();

    /** Parent -> count. Tallies aggregate counts for parents observed in conjunction with rare words. */
    private final Short2DoubleOpenHashMap rareWordParentCounts = new Short2DoubleOpenHashMap();

    /** Parent -> count */
    private final Short2DoubleOpenHashMap parentCounts = new Short2DoubleOpenHashMap();

    private final PackingFunction packingFunction;

    /**
     * Count threshold below which a word will be considered 'uncommon'. Lexical rules for uncommon words will be
     * smoothed with accumulated rare-word counts.
     */
    private final int uncommonWordThreshold;

    /**
     * Count threshold below which a word will be considered 'rare'. Tag -> rare word counts will be accumulated, for
     * smoothing with rare word and UNK-class counts.
     */
    private final int rareWordThreshold;

    /** Word-counts from the training corpus */
    private final Int2IntOpenHashMap corpusWordCounts;

    /**
     * Word-counts from the training corpus, for words occurring as the first word in a sentence. Used (along with
     * {@link #totalSentenceInitialCounts}) to estimate UNK-class probabilities for sentence-initial classes.
     */
    private final Int2IntOpenHashMap sentenceInitialCorpusWordCounts;

    public FractionalCountGrammar(final Vocabulary vocabulary, final MutableEnumeration<String> lexicon,
            final PackingFunction packingFunction, final Int2IntOpenHashMap corpusWordCounts,
            final Int2IntOpenHashMap sentenceInitialCorpusWordCounts, final int uncommonWordThreshold,
            final int rareWordThreshold) {

        this.vocabulary = vocabulary;
        this.lexicon = lexicon;
        this.startSymbol = vocabulary.getSymbol(0);

        this.packingFunction = packingFunction;
        this.parentCounts.defaultReturnValue(0);
        this.corpusWordCounts = corpusWordCounts;
        this.sentenceInitialCorpusWordCounts = sentenceInitialCorpusWordCounts;

        this.uncommonWordThreshold = uncommonWordThreshold;
        this.rareWordThreshold = rareWordThreshold;
    }

    public void incrementBinaryCount(final short parent, final short leftChild, final short rightChild,
            final double increment) {

        Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);
        if (leftChildMap == null) {
            leftChildMap = new Short2ObjectOpenHashMap<Short2DoubleOpenHashMap>();
            binaryRuleCounts.put(parent, leftChildMap);
        }

        Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);
        if (rightChildMap == null) {
            rightChildMap = new Short2DoubleOpenHashMap();
            rightChildMap.defaultReturnValue(0);
            leftChildMap.put(leftChild, rightChildMap);
        }

        rightChildMap.addTo(rightChild, increment);
        parentCounts.addTo(parent, increment);
    }

    public void incrementBinaryCount(final short parent, final int childPair, final double increment) {

        final short leftChild = (short) packingFunction.unpackLeftChild(childPair);
        final short rightChild = packingFunction.unpackRightChild(childPair);
        incrementBinaryCount(parent, leftChild, rightChild, increment);
    }

    public void incrementBinaryLogCount(final short parent, final short leftChild, final short rightChild,
            final float logIncrement) {

        assert (logIncrement <= .001f);
        final double increment = Math.exp(logIncrement);
        incrementBinaryCount(parent, leftChild, rightChild, increment);
    }

    /**
     * For unit tests
     */
    void incrementBinaryCount(final String parent, final String leftChild, final String rightChild,
            final float increment) {
        incrementBinaryCount((short) vocabulary.getIndex(parent), (short) vocabulary.getIndex(leftChild),
                (short) vocabulary.getIndex(rightChild), increment);
    }

    public void incrementUnaryCount(final short parent, final short child, final double increment) {

        Short2DoubleOpenHashMap childMap = unaryRuleCounts.get(parent);
        if (childMap == null) {
            childMap = new Short2DoubleOpenHashMap();
            childMap.defaultReturnValue(0);
            unaryRuleCounts.put(parent, childMap);
        }

        childMap.addTo(child, increment);
        parentCounts.addTo(parent, increment);
    }

    public void incrementUnaryLogCount(final short parent, final short child, final float logIncrement) {

        assert (logIncrement <= .001f);
        final double increment = Math.exp(logIncrement);
        incrementUnaryCount(parent, child, increment);
    }

    /**
     * For unit tests
     */
    void incrementUnaryCount(final String parent, final String child, final double increment) {
        incrementUnaryCount((short) vocabulary.getIndex(parent), (short) vocabulary.getIndex(child), increment);
    }

    public void incrementLexicalCount(final short parent, final int child, final double increment) {

        Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
        if (childMap == null) {
            childMap = new Int2DoubleOpenHashMap();
            childMap.defaultReturnValue(0);
            lexicalRuleCounts.put(parent, childMap);
        }

        childMap.addTo(child, increment);
        parentCounts.addTo(parent, increment);

        if (corpusWordCounts != null && corpusWordCounts.get(child) < rareWordThreshold) {
            rareWordParentCounts.addTo(parent, increment);
        }
    }

    protected void incrementLexicalLogCount(final short parent, final int child, final float logIncrement) {

        assert (logIncrement <= .001f);
        final double increment = Math.exp(logIncrement);
        incrementLexicalCount(parent, child, increment);
    }

    /**
     * For unit tests
     */
    void incrementLexicalCount(final String parent, final String child, final double increment) {
        incrementLexicalCount((short) vocabulary.getIndex(parent), lexicon.getIndex(child), increment);
    }

    public ArrayList<Production> binaryProductions(final float minimumRuleLogProbability) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            if (!binaryRuleCounts.containsKey(parent)) {
                continue;
            }

            final double parentCount = parentCounts.get(parent);
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                if (!leftChildMap.containsKey(leftChild)) {
                    continue;
                }

                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    if (!rightChildMap.containsKey(rightChild)) {
                        continue;
                    }

                    // Observations of this rule / Observations of all split rules with the parent
                    final double observations = rightChildMap.get(rightChild);
                    if (observations != 0) {
                        final float logProbability = (float) Math.log(observations / parentCount);
                        if (logProbability > minimumRuleLogProbability) {
                            prods.add(new Production(parent, leftChild, rightChild, logProbability, vocabulary, lexicon));
                        }
                    }
                }
            }
        }

        return prods;
    }

    public ArrayList<Production> unaryProductions(final float minimumRuleLogProbability) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            if (!unaryRuleCounts.containsKey(parent)) {
                continue;
            }

            final double parentCount = parentCounts.get(parent);
            final Short2DoubleOpenHashMap childMap = unaryRuleCounts.get(parent);

            for (short child = 0; child < vocabulary.size(); child++) {
                if (!childMap.containsKey(child)) {
                    continue;
                }

                // Observations of this rule / Observations of all rules with the parent
                final double count = childMap.get(child);
                // TODO Remove these redundant checks
                if (count != 0) {
                    final float logProbability = (float) Math.log(count / parentCount);
                    if (logProbability > minimumRuleLogProbability) {
                        prods.add(new Production(parent, child, logProbability, false, vocabulary, lexicon));
                    }
                }
            }
        }

        return prods;
    }

    public ArrayList<Production> lexicalProductions(final float minimumRuleLogProbability) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            if (!lexicalRuleCounts.containsKey(parent)) {
                continue;
            }
            final double parentCount = parentCounts.get(parent);

            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);

            for (int child = 0; child < lexicon.size(); child++) {
                if (!childMap.containsKey(child)) {
                    continue;
                }

                // Observations of this rule / Observations of all rules with the parent
                final double count = childMap.get(child);
                if (count != 0) {
                    final float logProbability = (float) Math.log(count / parentCount);
                    if (logProbability > minimumRuleLogProbability) {
                        prods.add(new Production(parent, child, logProbability, true, vocabulary, lexicon));
                    }
                }
            }
        }

        return prods;
    }

    /**
     * Adds counts for unobserved tag/word combinations, for words considered 'uncommon' (those occurring less than
     * {@link #uncommonWordThreshold} times in the training corpus).
     * 
     * @param openClassPreterminalThreshold Minimum number of terminal children a preterminal must have to be considered
     *            open-class. UNK-class rules will be created for open-class preterminals.
     * @param s_0
     * @param s_1
     * @param s_2
     * @return A copy of this grammar including UNK-class pseudo-counts based on observed counts of rare words.
     */
    public FractionalCountGrammar smooth(final int openClassPreterminalThreshold, final float s_0, final float s_1,
            final float s_2) {

        // TODO Clone parentCounts, so we don't modify it (and use the ever-changing version) while smoothing

        final FractionalCountGrammar smoothedGrammar = clone();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            // Skip parents without any lexical children and closed-class preterminals. An open-class preterminal is one
            // with more than 'openClassPreterminalThreshold' observed children.
            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
            if (childMap == null || childMap.size() < openClassPreterminalThreshold) {
                continue;
            }

            // c(T_x,r) - occurrences of the parent tag in conjunction with a rare word
            final double cTxr = rareWordParentCounts.get(parent);
            // p(r|T_x) - probability of a rare word given the parent tag
            final double pRTx = cTxr / parentCounts.get(parent);

            // Iterate over all observed children of the parent
            for (final int word : childMap.keySet()) {

                final int cw = corpusWordCounts.get(word);

                if (cw >= uncommonWordThreshold) {
                    smoothedGrammar.incrementLexicalCount(parent, word, s_0 * pRTx);
                } else {
                    smoothedGrammar.incrementLexicalCount(parent, word, s_1 * pRTx);
                }
            }
        }

        return smoothedGrammar;
    }

    /**
     * Returns a copy of this grammar including smoothed counts for all lexical productions of open-class preterminals
     * and UNK-class pseudo-counts based on observed counts of rare words.
     * 
     * Adds counts for:
     * <ul>
     * <li>Common words (c(w) > {@link #uncommonWordThreshold}): s_0 * c(T_x|r)</li>
     * <li>Uncommon words ({@link #rareWordThreshold} < c(w) <= {@link #uncommonWordThreshold}): s_1 * c(T_x|r)</li>
     * <li>Rare words (c(w) <= {@link #rareWordThreshold}): s_2 * c(T_x|r)</li>
     * </ul>
     * 
     * c(T_x|r) = occurrences of tag T_x in conjunction with a rare word (from {@link #rareWordParentCounts}).
     * 
     * @param unkClassMap Maps word entries from the lexicon to their UNK-class entries.
     * @param openClassPreterminalThreshold Minimum number of terminal children a preterminal must have to be considered
     *            open-class. UNK-class rules will be created for open-class preterminals.
     * @param s_0
     * @param s_1
     * @param s_2
     * @return A copy of this grammar including UNK-class pseudo-counts based on observed counts of rare words.
     */
    public FractionalCountGrammar addUnkCounts(final Int2IntOpenHashMap unkClassMap,
            final int openClassPreterminalThreshold, final float s_0, final float s_1, final float s_2) {

        final FractionalCountGrammar grammarWithUnks = clone();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            // Skip parents without any lexical children and closed-class preterminals. An open-class preterminal is one
            // with more than 'openClassPreterminalThreshold' observed children.
            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
            if (childMap == null || childMap.size() < openClassPreterminalThreshold) {
                continue;
            }

            // c(T_x|r) - occurrences of the parent tag in conjunction with a rare word
            final double cTxr = rareWordParentCounts.get(parent);
            // p(r|T_x) - probability of a rare word given the parent tag
            final double pRTx = cTxr / parentCounts.get(parent);

            for (final int word : childMap.keySet()) {

                final int cw = corpusWordCounts.get(word);
                if (cw < rareWordThreshold) {
                    // Sentence-initial counts
                    final int sentenceInitialCounts = sentenceInitialCorpusWordCounts.get(word);
                    final String sentenceInitialUnkClass = DecisionTreeTokenClassifier.berkeleyGetSignature(
                            lexicon.getSymbol(word), true, lexicon);
                    grammarWithUnks.incrementLexicalCount(parent, lexicon.getIndex(sentenceInitialUnkClass), s_2 * pRTx
                            * sentenceInitialCounts / cw);

                    // Other counts
                    final String unkClass = DecisionTreeTokenClassifier.berkeleyGetSignature(lexicon.getSymbol(word),
                            false, lexicon);
                    grammarWithUnks.incrementLexicalCount(parent, lexicon.getIndex(unkClass), s_2 * pRTx
                            * (1 - sentenceInitialCounts / cw));
                }
            }
        }

        return grammarWithUnks;
    }

    /**
     * Adds counts for unary productions which can be produced by a multi-step unary chain. e.g., if p(S -> SBAR) *
     * p(SBAR -> NP) > p(S -> NP), add a direct S -> NP production with the maximum probability.
     */
    public final void mergeUnaryChains() {
        // Iterate to find (an approximation of) the fixpoint. The true fixpoint would probably require |V| iterations,
        // but we don't usually have to iterate that long.
        for (int i = 0; i < 5; i++) {
            // Iterate over parents (e.g. S)
            for (final short parent : unaryRuleCounts.keySet()) {
                final double parentCount = parentCounts.get(parent);
                final Short2DoubleOpenHashMap directCounts = unaryRuleCounts.get(parent);

                // Iterate over children of those parents, the intermediate step in the unary chain (e.g. SBAR)
                final Short2DoubleOpenHashMap intermediateParentCounts = directCounts;
                for (final short intermediate : intermediateParentCounts.keySet()) {
                    final Short2DoubleOpenHashMap childCounts = unaryRuleCounts.get(intermediate);

                    if (childCounts == null) {
                        continue;
                    }
                    final double intermediateParentCount = parentCounts.get(intermediate);

                    // And finally over unary chain children (e.g. NP)
                    for (final short child : childCounts.keySet()) {

                        // Counts from parent -> intermediate and parent -> child
                        final double topIntCount = directCounts.get(intermediate);
                        final double topChildCount = directCounts.get(child);

                        // Existing probabilities for parent -> child, parent -> intermediate, and intermediate -> child
                        final double pExisting = topChildCount / parentCount;
                        final double pTopInt = intermediateParentCounts.get(intermediate) / parentCount;
                        final double pIntChild = childCounts.get(child) / intermediateParentCount;

                        if (pTopInt + pIntChild > pExisting) {
                            // Increase the count of the direct production to (almost) match the probability of the
                            // chain

                            // This operation will increase the parent count will increase too, so the chained
                            // probability will still be slightly higher than the 'direct' unary production. That's
                            // fine, since it prefers the chain observed in training data to the direct production, when
                            // possible.
                            incrementUnaryCount(parent, child, topIntCount * pIntChild - directCounts.get(child));
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the number of observations of a binary rule.
     * 
     * @param parent
     * @param leftChild
     * @param rightChild
     * @return the number of observations of a binary rule.
     */
    public final double binaryRuleObservations(final String parent, final String leftChild, final String rightChild) {

        final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get((short) vocabulary
                .getIndex(parent));
        if (leftChildMap == null) {
            return 0f;
        }

        final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get((short) vocabulary.getIndex(leftChild));
        if (rightChildMap == null) {
            return 0f;
        }

        return rightChildMap.get((short) vocabulary.getIndex(rightChild));
    }

    /**
     * Returns the (log) probability of a binary rule
     * 
     * @param parent
     * @param leftChild
     * @param rightChild
     * @return the (log) probability of a binary rule
     */
    public final float binaryLogProbability(final String parent, final String leftChild, final String rightChild) {
        return (float) Math.log(binaryRuleObservations(parent, leftChild, rightChild)
                / parentCounts.get((short) vocabulary.getIndex(parent)));
    }

    /**
     * Returns the number of observations of a unary rule.
     * 
     * @param parent
     * @param child
     * @return the number of observations of a unary rule.
     */
    public final double unaryRuleObservations(final String parent, final String child) {

        final Short2DoubleOpenHashMap childMap = unaryRuleCounts.get((short) vocabulary.getIndex(parent));
        if (childMap == null) {
            return 0f;
        }

        return childMap.get((short) vocabulary.getIndex(child));
    }

    /**
     * Returns the (log) probability of a unary rule
     * 
     * @param parent
     * @param child
     * @return the (log) probability of a unary rule
     */
    public final float unaryLogProbability(final String parent, final String child) {
        return (float) Math.log(unaryRuleObservations(parent, child)
                / parentCounts.get((short) vocabulary.getIndex(parent)));
    }

    /**
     * Returns the number of observations of a lexical rule.
     * 
     * @param parent
     * @param child
     * @return the number of observations of a lexical rule.
     */
    public final double lexicalRuleObservations(final String parent, final String child) {
        final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get((short) vocabulary.getIndex(parent));
        if (childMap == null) {
            return 0f;
        }

        return childMap.get(lexicon.getIndex(child));
    }

    /**
     * Returns the (log) probability of a lexical rule
     * 
     * @param parent
     * @param child
     * @return the (log) probability of a lexical rule
     */
    public final float lexicalLogProbability(final String parent, final String child) {
        return (float) Math.log(lexicalRuleObservations(parent, child)
                / parentCounts.get((short) vocabulary.getIndex(parent)));
    }

    /**
     * Splits each non-terminal in the grammar into 2 sub-states, constructing a new grammar, vocabulary, and lexicon.
     * 
     * @return Newly-constructed grammar
     */
    public FractionalCountGrammar split(final NoiseGenerator noiseGenerator) {

        // Create a new vocabulary, splitting each non-terminal into two substates
        final SplitVocabulary splitVocabulary = ((SplitVocabulary) vocabulary).split();

        final FractionalCountGrammar splitGrammar = new FractionalCountGrammar(splitVocabulary, lexicon, null,
                corpusWordCounts, sentenceInitialCorpusWordCounts, uncommonWordThreshold, rareWordThreshold);

        //
        // Iterate through each rule, creating split rules in the new grammar
        //

        // Split each binary production into 8
        for (final short parent : binaryRuleCounts.keySet()) {
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);
            final short splitParent0 = (short) (parent << 1);
            final short splitParent1 = (short) (splitParent0 + 1);
            assert splitParent1 < splitVocabulary.size();

            for (final short leftChild : leftChildMap.keySet()) {
                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);
                final short splitLeftChild0 = (short) (leftChild << 1);
                final short splitLeftChild1 = (short) (splitLeftChild0 + 1);
                assert splitLeftChild1 < splitVocabulary.size();

                for (final short rightChild : rightChildMap.keySet()) {
                    final short splitRightChild0 = (short) (rightChild << 1);
                    final short splitRightChild1 = (short) (splitRightChild0 + 1);
                    assert splitRightChild1 < splitVocabulary.size();

                    final double count = rightChildMap.get(rightChild) / 4;

                    double noise = noiseGenerator.noise(count);
                    splitGrammar.incrementBinaryCount(splitParent0, splitLeftChild0, splitRightChild0, count + noise);
                    splitGrammar.incrementBinaryCount(splitParent0, splitLeftChild0, splitRightChild1, count - noise);
                    noise = noiseGenerator.noise(count);
                    splitGrammar.incrementBinaryCount(splitParent0, splitLeftChild1, splitRightChild0, count + noise);
                    splitGrammar.incrementBinaryCount(splitParent0, splitLeftChild1, splitRightChild1, count - noise);
                    noise = noiseGenerator.noise(count);
                    splitGrammar.incrementBinaryCount(splitParent1, splitLeftChild0, splitRightChild0, count + noise);
                    splitGrammar.incrementBinaryCount(splitParent1, splitLeftChild0, splitRightChild1, count - noise);
                    noise = noiseGenerator.noise(count);
                    splitGrammar.incrementBinaryCount(splitParent1, splitLeftChild1, splitRightChild0, count + noise);
                    splitGrammar.incrementBinaryCount(splitParent1, splitLeftChild1, splitRightChild1, count - noise);
                }
            }
        }

        // Split each unary production into fourths
        for (final short parent : unaryRuleCounts.keySet()) {
            final Short2DoubleOpenHashMap childMap = unaryRuleCounts.get(parent);
            final short splitParent0 = (short) (parent << 1);
            final short splitParent1 = (short) (splitParent0 + 1);
            assert splitParent1 < splitVocabulary.size();

            // Special-case for the start symbol. Since we do not split it, we only split unaries of which it is the
            // parent in two
            if (parent == 0) {
                for (final short child : childMap.keySet()) {
                    final short splitChild0 = (short) (child << 1);
                    final short splitChild1 = (short) (splitChild0 + 1);
                    assert splitChild1 < splitVocabulary.size();

                    final double count = childMap.get(child);

                    final double noise = noiseGenerator.noise(count);
                    splitGrammar.incrementUnaryCount(parent, splitChild0, count + noise);
                    splitGrammar.incrementUnaryCount(parent, splitChild1, count - noise);
                }

            } else {
                for (final short child : childMap.keySet()) {
                    final short splitChild0 = (short) (child << 1);
                    final short splitChild1 = (short) (splitChild0 + 1);
                    assert splitChild1 < splitVocabulary.size();

                    final double count = childMap.get(child) / 2;

                    double noise = noiseGenerator.noise(count);
                    splitGrammar.incrementUnaryCount(splitParent0, splitChild0, count + noise);
                    splitGrammar.incrementUnaryCount(splitParent0, splitChild1, count - noise);
                    noise = noiseGenerator.noise(count);
                    splitGrammar.incrementUnaryCount(splitParent1, splitChild0, count + noise);
                    splitGrammar.incrementUnaryCount(splitParent1, splitChild1, count - noise);
                }
            }
        }

        // Split lexical productions in half. Each split production has the same probability as the original production
        for (final short parent : lexicalRuleCounts.keySet()) {
            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
            final short splitParent0 = (short) (parent << 1);
            final short splitParent1 = (short) (splitParent0 + 1);
            assert splitParent1 < splitVocabulary.size();

            for (final int child : childMap.keySet()) {

                final double count = childMap.get(child);
                splitGrammar.incrementLexicalCount(splitParent0, child, count);
                splitGrammar.incrementLexicalCount(splitParent1, child, count);
            }
        }

        return splitGrammar;
    }

    /**
     * Adds random pseudo-count noise to each rule. The number of pseudo-counts is proportional to the observed parent
     * count.
     */
    @Deprecated
    public void randomize(final NoiseGenerator noiseGenerator, final float amount) {

        // Short-circuit if we won't add any pseudo-counts
        if (amount == 0) {
            return;
        }

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> binaryLeftChildMap = binaryRuleCounts.get(parent);
            final Short2DoubleOpenHashMap unaryChildMap = unaryRuleCounts.get(parent);
            final Int2DoubleOpenHashMap lexicalChildMap = lexicalRuleCounts.get(parent);

            // Binary rules
            if (binaryLeftChildMap != null) {
                for (final short leftChild : binaryLeftChildMap.keySet()) {
                    final Short2DoubleOpenHashMap binaryRightChildMap = binaryLeftChildMap.get(leftChild);

                    for (final short rightChild : binaryRightChildMap.keySet()) {
                        incrementBinaryCount(parent, leftChild, rightChild, noiseGenerator.noise() * 100 * amount);
                    }
                }
            }

            // Unary rules
            if (unaryChildMap != null) {
                for (final short child : unaryChildMap.keySet()) {
                    incrementUnaryCount(parent, child, noiseGenerator.noise() * 100 * amount);
                }
            }

            // Lexical rules
            if (lexicalChildMap != null) {
                for (final int child : lexicalChildMap.keySet()) {
                    incrementLexicalCount(parent, child, noiseGenerator.noise() * 100 * amount);
                }
            }
        }
    }

    /**
     * Re-merges splits specified by non-terminal indices, producing a new {@link FractionalCountGrammar} with its own
     * vocabulary and lexicon.
     * 
     * @param indices Non-terminal indices to merge. Each index is assumed to be the <i>second</i> of a split pair.
     *            i.e., if A and B were split, merging A into B is equivalent to merging B into A. The merge operation
     *            assumes that the indices will be of each non-terminal B.
     * @return Merged grammar
     */
    public FractionalCountGrammar merge(final short[] indices) {

        // Create merged vocabulary and map from old vocabulary indices to new
        final Short2ShortOpenHashMap parentToMergedIndexMap = new Short2ShortOpenHashMap();

        // The set of post-merge indices which were merged 'into'
        final ShortOpenHashSet mergedIndices = new ShortOpenHashSet();

        short j = 0;
        Arrays.sort(indices);
        final ArrayList<String> mergedSymbols = new ArrayList<String>();
        mergedSymbols.add(startSymbol);

        String previousRoot = "";
        byte nextSubstate = 0;

        for (short i = 1; i < vocabulary.size(); i++) {
            if (j < indices.length && indices[j] == i) {
                j++;
                mergedIndices.add((short) (mergedSymbols.size() - 1));
            } else {
                // This would be much shorter and clearer if Java had tuples...
                final String mergedRoot = vocabulary.getSymbol(i).split("_")[0];

                if (mergedRoot.equals(previousRoot)) {
                    // Add the next split index in order, which may not match that of the split grammar symbol
                    mergedSymbols.add(previousRoot + '_' + nextSubstate);
                    nextSubstate++;
                } else {
                    mergedSymbols.add(mergedRoot + "_0");
                    nextSubstate = 1;
                }
                previousRoot = mergedRoot;
            }
            parentToMergedIndexMap.put(i, (short) (mergedSymbols.size() - 1));
        }

        final SplitVocabulary mergedVocabulary = new SplitVocabulary(mergedSymbols, vocabulary, parentToMergedIndexMap,
                mergedIndices);

        //
        // Create a new grammar, populated with all binary, unary, and lexical rule counts from this grammar
        //
        final FractionalCountGrammar mergedGrammar = new FractionalCountGrammar(mergedVocabulary, lexicon, null,
                corpusWordCounts, sentenceInitialCorpusWordCounts, uncommonWordThreshold, rareWordThreshold);

        // Binary
        for (final short parent : binaryRuleCounts.keySet()) {
            final short mergedParent = parentToMergedIndexMap.get(parent);
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (final short leftChild : leftChildMap.keySet()) {
                final short mergedLeftChild = parentToMergedIndexMap.get(leftChild);
                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                for (final short rightChild : rightChildMap.keySet()) {
                    mergedGrammar.incrementBinaryCount(mergedParent, mergedLeftChild,
                            parentToMergedIndexMap.get(rightChild), rightChildMap.get(rightChild));
                }
            }
        }

        // Unary
        for (final short parent : unaryRuleCounts.keySet()) {
            final short mergedParent = parentToMergedIndexMap.get(parent);
            final Short2DoubleOpenHashMap childMap = unaryRuleCounts.get(parent);

            for (final short child : childMap.keySet()) {
                mergedGrammar.incrementUnaryCount(mergedParent, parentToMergedIndexMap.get(child), childMap.get(child));
            }
        }

        // Lexical
        for (final short parent : lexicalRuleCounts.keySet()) {
            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
            final short mergedParent = parentToMergedIndexMap.get(parent);

            for (final int child : childMap.keySet()) {
                mergedGrammar.incrementLexicalCount(mergedParent, child, childMap.get(child));
            }
        }

        return mergedGrammar;
    }

    /**
     * Performs a 'partial' merge, merging counts but <em>not</em> non-terminals. This creates a 'merged' grammar with
     * which we can still perform constrained parses using the current {@link ConstrainingChart}, allowing post-merge
     * EM. To complete the partial merge, call {@link #merge(short[])}.
     * 
     * @param indices Non-terminal indices to merge. Each index is assumed to be the <i>second</i> of a split pair.
     *            i.e., if A and B were split, merging A into B is equivalent to merging B into A. The merge operation
     *            assumes that the indices will be of each non-terminal B.
     * @return Merged grammar
     */
    @Deprecated
    public FractionalCountGrammar mergeCounts(final short[] indices) {

        // Post-merge indices, indexed by pre-merge values
        final short[] mergedIndices = new short[vocabulary.size()];
        for (short i = 0, j = 0; i < mergedIndices.length; i++) {
            if (j < indices.length && indices[j] == i) {
                mergedIndices[i] = (short) (i - 1);
            } else {
                mergedIndices[i] = i;
            }
        }

        //
        // Create a new grammar, populated with all binary, unary, and lexical rule counts from this grammar
        //
        final FractionalCountGrammar mergedGrammar = new FractionalCountGrammar(vocabulary, lexicon, null,
                corpusWordCounts, sentenceInitialCorpusWordCounts, uncommonWordThreshold, rareWordThreshold);

        for (final short parent : binaryRuleCounts.keySet()) {
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);
            for (final short leftChild : leftChildMap.keySet()) {
                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);
                for (final short rightChild : rightChildMap.keySet()) {
                    mergedGrammar.incrementBinaryCount(mergedIndices[parent], mergedIndices[leftChild],
                            mergedIndices[rightChild], rightChildMap.get(rightChild));
                }
            }
        }

        for (final short parent : unaryRuleCounts.keySet()) {
            final Short2DoubleOpenHashMap childMap = unaryRuleCounts.get(parent);
            for (final short child : childMap.keySet()) {
                mergedGrammar.incrementUnaryCount(mergedIndices[parent], mergedIndices[child], childMap.get(child));
            }
        }

        for (final short parent : lexicalRuleCounts.keySet()) {
            final Int2DoubleOpenHashMap childMap = lexicalRuleCounts.get(parent);
            for (final int child : childMap.keySet()) {
                mergedGrammar.incrementLexicalCount(mergedIndices[parent], child, childMap.get(child));
            }
        }

        return mergedGrammar;
    }

    /**
     * Computes fractional counts of each split pair. E.g., if NP_0 has been split into NP_0 and NP_1, and NP_0 occurs
     * 12 times and NP_1 8, the array entries for NP_0 and NP_1 will contain (log) 12/20 and 8/20 respectively.
     * 
     * @return fractional counts of each non-terminal.
     */
    public float[] logSplitFraction() {
        final float[] logSplitCounts = new float[vocabulary.size()];
        for (short i = 0; i < logSplitCounts.length; i += 2) {
            final double split0Count = parentCounts.get(i);
            final double split1Count = parentCounts.get((short) (i + 1));
            final double totalCount = split0Count + split1Count;
            logSplitCounts[i] = (float) Math.log(split0Count / totalCount);
            logSplitCounts[i + 1] = (float) Math.log(split1Count / totalCount);
        }
        return logSplitCounts;
    }

    /**
     * Returns the reduction in rule-count if each pair of non-terminals is merged. These counts are exact for each
     * possible merge (e.g., NP_1 into NP_0), but do not account for the interactions between multiple simultaneous
     * non-terminal merges. I.e., these counts will always be an overestimate of the rule-count savings when multiple
     * NTs are merged en-masse.
     * 
     * The returned array is indexed by non-terminal index / 2, and contains separate counts for binary, unary, and
     * lexical rules.
     * 
     * @return Array of rule-count savings, indexed by non-terminal index / 2, and in the second dimension by 0, 1, 2
     *         (binary, unary, and lexical)
     */
    public int[][] estimateMergeRuleCountDelta() {
        final int[][] ruleCountDelta = new int[vocabulary.size()][3];

        //
        // Binary productions
        //

        // These maps indicate presence or absence of binary rules by parent, left child and right child.
        // Parent -> left child -> right children */
        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> parentBinaryRules = parentBinaryRules();
        // Left child -> parent -> right children */
        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> leftChildBinaryRules = leftChildBinaryRules();
        // Right child -> parent -> left children */
        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> rightChildBinaryRules = rightChildBinaryRules();

        for (final Production p : binaryProductions(Float.NEGATIVE_INFINITY)) {

            // First, consider merging the parent (skip even parents, since they will be merged into)
            if (p.parent % 2 != 0
                    && contains(parentBinaryRules, (short) (p.parent - 1), (short) p.leftChild, (short) p.rightChild)) {
                ruleCountDelta[p.parent / 2][0]++;
            }

            // Now, the left child
            if (p.leftChild % 2 != 0
                    && contains(leftChildBinaryRules, (short) p.parent, (short) (p.leftChild - 1), (short) p.rightChild)) {
                ruleCountDelta[p.leftChild / 2][0]++;
            }

            // And finally, the right child
            if (p.rightChild % 2 != 0
                    && contains(rightChildBinaryRules, (short) p.parent, (short) p.leftChild,
                            (short) (p.rightChild - 1))) {
                ruleCountDelta[p.rightChild / 2][0]++;
            }
        }

        //
        // Unary productions
        //
        for (final Production p : unaryProductions(Float.NEGATIVE_INFINITY)) {
            // Consider merging the parent
            if (p.parent % 2 != 0 && unaryRuleCounts.containsKey((short) (p.parent - 1))
                    && unaryRuleCounts.get((short) (p.parent - 1)).containsKey((short) p.leftChild)) {
                ruleCountDelta[p.parent / 2][1]++;
            }

            // And the child
            if (p.leftChild % 2 != 0 && unaryRuleCounts.containsKey((short) p.parent)
                    && unaryRuleCounts.get((short) p.parent).containsKey((short) (p.leftChild - 1))) {
                ruleCountDelta[p.leftChild / 2][1]++;
            }
        }

        //
        // Lexical productions
        //
        for (final Production p : lexicalProductions(Float.NEGATIVE_INFINITY)) {
            if (p.parent % 2 != 0 && lexicalRuleCounts.containsKey((short) (p.parent - 1))
                    && lexicalRuleCounts.get((short) (p.parent - 1)).containsKey(p.leftChild)) {
                ruleCountDelta[p.parent / 2][2]++;
            }
        }

        return ruleCountDelta;
    }

    /**
     * @return a data structure indicating presence or absence of binary rules by parent (parent -> left child -> right
     *         children).
     */
    private Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> parentBinaryRules() {

        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> parentBinaryRules = new Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            if (!binaryRuleCounts.containsKey(parent)) {
                continue;
            }

            if (!parentBinaryRules.containsKey(parent)) {
                parentBinaryRules.put(parent, new Short2ObjectOpenHashMap<ShortOpenHashSet>());
            }

            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                if (!leftChildMap.containsKey(leftChild)) {
                    continue;
                }

                if (!parentBinaryRules.get(parent).containsKey(leftChild)) {
                    parentBinaryRules.get(parent).put(leftChild, new ShortOpenHashSet());
                }

                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    if (rightChildMap.containsKey(rightChild)) {
                        parentBinaryRules.get(parent).get(leftChild).add(rightChild);
                    }
                }
            }
        }

        return parentBinaryRules;
    }

    /**
     * @return a data structure indicating presence or absence of binary rules by left child (left child -> parent ->
     *         right children).
     */
    private Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> leftChildBinaryRules() {

        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> leftChildBinaryRules = new Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            if (!binaryRuleCounts.containsKey(parent)) {
                continue;
            }

            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                if (!leftChildMap.containsKey(leftChild)) {
                    continue;
                }

                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                if (!leftChildBinaryRules.containsKey(leftChild)) {
                    leftChildBinaryRules.put(leftChild, new Short2ObjectOpenHashMap<ShortOpenHashSet>());
                }

                if (!leftChildBinaryRules.get(leftChild).containsKey(parent)) {
                    leftChildBinaryRules.get(leftChild).put(parent, new ShortOpenHashSet());
                }

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    if (rightChildMap.containsKey(rightChild)) {
                        leftChildBinaryRules.get(leftChild).get(parent).add(rightChild);
                    }
                }
            }
        }

        return leftChildBinaryRules;
    }

    /**
     * @return a data structure indicating presence or absence of binary rules by right child (right child -> parent ->
     *         left children).
     */
    private Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> rightChildBinaryRules() {

        final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> rightChildBinaryRules = new Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>>();

        for (short parent = 0; parent < vocabulary.size(); parent++) {

            if (!binaryRuleCounts.containsKey(parent)) {
                continue;
            }

            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                if (!leftChildMap.containsKey(leftChild)) {
                    continue;
                }

                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    if (!rightChildMap.containsKey(rightChild)) {
                        continue;
                    }

                    if (!rightChildBinaryRules.containsKey(rightChild)) {
                        rightChildBinaryRules.put(rightChild, new Short2ObjectOpenHashMap<ShortOpenHashSet>());
                    }

                    if (!rightChildBinaryRules.get(rightChild).containsKey(parent)) {
                        rightChildBinaryRules.get(rightChild).put(parent, new ShortOpenHashSet());
                    }

                    rightChildBinaryRules.get(rightChild).get(parent).add(leftChild);
                }
            }
        }

        return rightChildBinaryRules;
    }

    private boolean contains(final Short2ObjectOpenHashMap<Short2ObjectOpenHashMap<ShortOpenHashSet>> map,
            final short x, final short y, final short z) {

        final Short2ObjectOpenHashMap<ShortOpenHashSet> map2 = map.get(x);
        if (map2 == null) {
            return false;
        }

        final ShortOpenHashSet set = map2.get(y);
        if (set == null) {
            return false;
        }

        return set.contains(z);
    }

    public final int totalRules() {
        return binaryRules() + unaryRules() + lexicalRules();
    }

    public final int binaryRules() {
        int count = 0;
        for (final short parent : binaryRuleCounts.keySet()) {
            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> leftChildMap = binaryRuleCounts.get(parent);

            for (final short leftChild : leftChildMap.keySet()) {
                final Short2DoubleOpenHashMap rightChildMap = leftChildMap.get(leftChild);

                count += rightChildMap.size();
            }
        }
        return count;
    }

    public final int unaryRules() {
        int count = 0;
        for (final short parent : unaryRuleCounts.keySet()) {
            count += unaryRuleCounts.get(parent).size();
        }
        return count;
    }

    public final int lexicalRules() {
        int count = 0;
        for (final short parent : lexicalRuleCounts.keySet()) {
            count += lexicalRuleCounts.get(parent).size();
        }
        return count;
    }

    public final double observations(final String parent) {
        throw new UnsupportedOperationException();
    }

    public double totalParentCounts() {
        double totalParentCounts = 0;
        for (final double count : parentCounts.values()) {
            totalParentCounts += count;
        }
        return totalParentCounts;
    }

    @Override
    public FractionalCountGrammar clone() {
        return clone(Float.NEGATIVE_INFINITY);
    }

    public FractionalCountGrammar clone(final float minimumRuleLogProbability) {
        final FractionalCountGrammar clone = new FractionalCountGrammar(vocabulary, lexicon, packingFunction,
                corpusWordCounts, sentenceInitialCorpusWordCounts, uncommonWordThreshold, rareWordThreshold);

        for (short parent = 0; parent < vocabulary.size(); parent++) {
            final double threshold = minimumRuleLogProbability > Float.NEGATIVE_INFINITY ? parentCounts.get(parent)
                    * Math.exp(minimumRuleLogProbability) : 0;

            final Short2ObjectOpenHashMap<Short2DoubleOpenHashMap> binaryLeftChildMap = binaryRuleCounts.get(parent);
            final Short2DoubleOpenHashMap unaryChildMap = unaryRuleCounts.get(parent);
            final Int2DoubleOpenHashMap lexicalChildMap = lexicalRuleCounts.get(parent);

            // Binary rules
            if (binaryLeftChildMap != null) {
                for (final short leftChild : binaryLeftChildMap.keySet()) {
                    final Short2DoubleOpenHashMap binaryRightChildMap = binaryLeftChildMap.get(leftChild);

                    for (final short rightChild : binaryRightChildMap.keySet()) {
                        final double count = binaryRightChildMap.get(rightChild);
                        if (count > threshold) {
                            clone.incrementBinaryCount(parent, leftChild, rightChild, count);
                        }
                    }
                }
            }

            // Unary rules
            if (unaryChildMap != null) {
                for (final short child : unaryChildMap.keySet()) {
                    final double count = unaryChildMap.get(child);
                    if (count > threshold) {
                        clone.incrementUnaryCount(parent, child, count);
                    }
                }
            }

            // Lexical rules
            if (lexicalChildMap != null) {
                for (final int child : lexicalChildMap.keySet()) {
                    final double count = lexicalChildMap.get(child);
                    if (count > threshold) {
                        clone.incrementLexicalCount(parent, child, count);
                    }
                }
            }
        }
        return clone;
    }

    @Override
    public String toString() {
        return toString(false, null, null, -1);
    }

    public String toString(final boolean fraction, final Language language, final GrammarFormatType grammarFormatType,
            final int lexicalUnkThreshold) {
        try {
            final StringWriter w = new StringWriter(totalRules() * 20);
            write(new PrintWriter(w), fraction, language, grammarFormatType, lexicalUnkThreshold);
            return w.toString();
        } catch (final IOException e) {
            // StringWriter should never IOException
            return null;
        }
    }

    public void write(final PrintWriter writer, final boolean fraction, final Language language,
            final GrammarFormatType grammarFormatType, final int lexicalUnkThreshold) throws IOException {

        final BufferedWriter bw = new BufferedWriter(writer);

        // TODO Consolidate into base Grammar class
        final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        final String dateNowStr = dateFormat.format(new Date());

        final int nBinary = binaryRules();
        final int nUnary = unaryRules();
        final int nLex = lexicalRules();

        final StringBuilder sb = new StringBuilder(256);
        sb.append("lang=" + (language != null ? language : "UNK"));
        sb.append(" format=" + grammarFormatType);
        sb.append(" unkThresh=" + lexicalUnkThreshold);
        sb.append(" start=" + startSymbol);
        sb.append(" hMarkov=UNK");
        sb.append(" vMarkov=UNK");
        sb.append(" date=" + dateNowStr);
        sb.append(" vocabSize=" + vocabulary.size());
        sb.append(" nBinary=" + nBinary);
        sb.append(" nUnary=" + nUnary);
        sb.append(" nLex=" + nLex);
        sb.append("\n");
        bw.write(sb.toString());

        for (final Production p : binaryProductions(Float.NEGATIVE_INFINITY)) {
            if (fraction) {
                bw.write(String.format("%s -> %s %s %s\n", vocabulary.getSymbol(p.parent),
                        vocabulary.getSymbol(p.leftChild), vocabulary.getSymbol(p.rightChild), Strings.fraction(p.prob)));
            } else {
                bw.write(String.format("%s -> %s %s %.6f\n", vocabulary.getSymbol(p.parent),
                        vocabulary.getSymbol(p.leftChild), vocabulary.getSymbol(p.rightChild), p.prob));
            }
        }

        for (final Production p : unaryProductions(Float.NEGATIVE_INFINITY)) {
            if (fraction) {
                bw.write(String.format("%s -> %s %s\n", vocabulary.getSymbol(p.parent),
                        vocabulary.getSymbol(p.leftChild), Strings.fraction(p.prob)));
            } else {
                bw.write(String.format("%s -> %s %.6f\n", vocabulary.getSymbol(p.parent),
                        vocabulary.getSymbol(p.leftChild), p.prob));
            }
        }

        bw.write(Grammar.LEXICON_DELIMITER + '\n');
        for (final Production p : lexicalProductions(Float.NEGATIVE_INFINITY)) {
            if (fraction) {
                bw.write(String.format("%s -> %s %s\n", vocabulary.getSymbol(p.parent), lexicon.getSymbol(p.leftChild),
                        Strings.fraction(p.prob)));
            } else {
                bw.write(String.format("%s -> %s %.6f\n", vocabulary.getSymbol(p.parent),
                        lexicon.getSymbol(p.leftChild), p.prob));
            }
        }
        bw.flush();
    }

    public static interface NoiseGenerator {

        /**
         * Returns generated 'noise' (generally random, depending on the implementation), scaled by the supplied count.
         * 
         * @param count
         * @return Noise, scaled by the supplied count
         */
        public double noise(final double count);

        /**
         * Returns generated 'noise' (generally random, depending on the implementation).
         * 
         * @return Noise
         */
        public double noise();
    }

    public static class ZeroNoiseGenerator implements NoiseGenerator {

        @Override
        public double noise(final double count) {
            return 0;
        }

        @Override
        public double noise() {
            return 0;
        }
    }

    public static class RandomNoiseGenerator implements NoiseGenerator {

        private final Random random;
        private final float amount;

        /**
         * @param seed Random seed
         * @param amount Amount of randomness (0-1) to add to the rule probabilities in the new grammar (e.g., if
         *            <code>amount</code> is 0.01, each pair differs by 1%). With 0 noise, the probabilities of each
         *            rule will be split equally. Some noise is generally required to break ties in the new grammar.
         */
        public RandomNoiseGenerator(final long seed, final float amount) {
            this.random = new Random(seed);
            this.amount = amount;
        }

        @Override
        public double noise(final double count) {
            return count * (random.nextDouble() - .5) * amount;
        }

        @Override
        public double noise() {
            return random.nextDouble();
        }
    }
}
