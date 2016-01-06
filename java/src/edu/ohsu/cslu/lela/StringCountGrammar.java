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

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;

import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree.Binarization;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.util.MutableEnumeration;

/**
 * Grammar computed from observation counts in a training corpus. Generally used for initial induction of a Markov-0
 * grammar from a treebank.
 * 
 * @author Aaron Dunlop
 * @since Jan 13, 2011
 */
public final class StringCountGrammar implements CountGrammar {

    /**
     * Contains occurrence counts for each non-terminal which occurs as a binary parent. When representing the grammar
     * for inside-outside re-estimation, we may be able to save space by not creating certain data structures for
     * non-terminals which don't occur as binary parents. And we may be able to save execution time by sorting other
     * data structures according to frequency counts.
     */
    final Object2FloatMap<String> binaryParentCounts = new Object2FloatOpenHashMap<String>();

    /** Occurrence counts for each non-terminal which occurs as a unary parent. */
    final Object2FloatMap<String> unaryParentCounts = new Object2FloatOpenHashMap<String>();

    /** Occurrence counts for each non-terminal which occurs as a lexical parent. */
    final Object2FloatMap<String> lexicalParentCounts = new Object2FloatOpenHashMap<String>();

    private final HashMap<String, HashMap<String, Object2FloatMap<String>>> binaryRuleCounts = new HashMap<String, HashMap<String, Object2FloatMap<String>>>();
    private final HashMap<String, Object2FloatMap<String>> unaryRuleCounts = new HashMap<String, Object2FloatMap<String>>();
    private final HashMap<String, Object2FloatMap<String>> lexicalRuleCounts = new HashMap<String, Object2FloatMap<String>>();
    private final Object2FloatMap<String> sentenceInitialWordCounts = new Object2FloatOpenHashMap<String>();

    private float totalBinaryRuleCounts;
    private float totalUnaryRuleCounts;
    private float totalLexicalRuleCounts;

    private int binaryRules;
    private int unaryRules;
    private int lexicalRules;

    String startSymbol;

    private final LinkedHashSet<String> observedNonTerminals = new LinkedHashSet<String>();
    private final Object2FloatOpenHashMap<String> lexicalEntryOccurrences = new Object2FloatOpenHashMap<String>();

    /**
     * Induces a grammar from a treebank, formatted in standard Penn-Treebank format, one bracketed sentence per line.
     * 
     * The non-terminal and terminal vocabularies induced (V and T) will be mapped in the order of observation in the
     * original treebank.
     * 
     * @param reader
     * @param binarization Binarization direction. If null, the tree is assumed to be already binarized.
     * @param grammarFormatType Grammar format used in factorization. If null, the tree is assumed to be already
     *            binarized.
     * @throws IOException
     */
    public StringCountGrammar(final Reader reader, final Binarization binarization,
            final GrammarFormatType grammarFormatType) throws IOException {

        // Temporary string-based maps recording counts of binary, unary, and
        // lexical rules. We will transfer these counts to more compact index-mapped
        // maps after collapsing unknown words in the lexicon

        final BufferedReader br = new BufferedReader(reader);

        // Get word counts from corpus
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            readLine(line, binarization, grammarFormatType, 1);
        }
    }

    public void readLine(final String line, final Binarization binarization, final GrammarFormatType grammarFormatType,
            final float increment) {
        BinaryTree<String> tree;

        // Skip empty trees
        if (line.equals("()") || line.equals("")) {
            return;
        }

        if (binarization == null) {
            tree = BinaryTree.read(line, String.class);
        } else {
            tree = NaryTree.read(line, String.class).binarize(grammarFormatType, binarization);
        }

        if (startSymbol == null) {
            setStartSymbol(tree.label());
        }

        incrementSentenceInitialCount(tree.leftmostLeaf().label(), increment);

        for (final BinaryTree<String> node : tree.inOrderTraversal()) {
            // Skip leaf nodes - only internal nodes are parents
            if (node.isLeaf()) {
                continue;
            }

            final String parent = node.label().intern();
            final String leftChild = node.leftChild().label().intern();

            if (node.rightChild() != null) {
                // Binary rule
                final String rightChild = node.rightChild().label().intern();
                incrementBinaryCount(parent, leftChild, rightChild, increment);

            } else {
                if (node.leftChild().isLeaf()) {
                    // Lexical rule
                    incrementLexicalCount(parent, leftChild, increment);
                } else {
                    // Unary rule
                    incrementUnaryCount(parent, leftChild, increment);
                }
            }
        }
    }

    public void setStartSymbol(final String startSymbol) {
        this.startSymbol = startSymbol;
        observedNonTerminals.add(startSymbol);
    }

    void incrementBinaryCount(final String parent, final String leftChild, final String rightChild,
            final float increment) {

        observedNonTerminals.add(parent);
        observedNonTerminals.add(leftChild);
        observedNonTerminals.add(rightChild);
        binaryParentCounts.put(parent, binaryParentCounts.getFloat(parent) + increment);

        HashMap<String, Object2FloatMap<String>> leftChildMap = binaryRuleCounts.get(parent);
        if (leftChildMap == null) {
            leftChildMap = new HashMap<String, Object2FloatMap<String>>();
            binaryRuleCounts.put(parent, leftChildMap);
        }

        Object2FloatMap<String> rightChildMap = leftChildMap.get(leftChild);
        if (rightChildMap == null) {
            rightChildMap = new Object2FloatOpenHashMap<String>();
            leftChildMap.put(leftChild, rightChildMap);
        }

        if (!rightChildMap.containsKey(rightChild)) {
            binaryRules++;
        }

        rightChildMap.put(rightChild, rightChildMap.getFloat(rightChild) + increment);
        totalBinaryRuleCounts += increment;
    }

    void incrementUnaryCount(final String parent, final String child, final float increment) {

        observedNonTerminals.add(parent);
        observedNonTerminals.add(child);
        unaryParentCounts.put(parent, unaryParentCounts.getFloat(parent) + increment);

        Object2FloatMap<String> childMap = unaryRuleCounts.get(parent);
        if (childMap == null) {
            childMap = new Object2FloatOpenHashMap<String>();
            unaryRuleCounts.put(parent, childMap);
        }

        if (!childMap.containsKey(child)) {
            unaryRules++;
        }

        childMap.put(child, childMap.getFloat(child) + increment);
        totalUnaryRuleCounts += increment;
    }

    void incrementLexicalCount(final String parent, final String child, final float increment) {

        observedNonTerminals.add(parent);
        lexicalEntryOccurrences.put(child, lexicalEntryOccurrences.getFloat(child) + increment);
        lexicalParentCounts.put(parent, lexicalParentCounts.getFloat(parent) + increment);

        Object2FloatMap<String> childMap = lexicalRuleCounts.get(parent);
        if (childMap == null) {
            childMap = new Object2FloatOpenHashMap<String>();
            lexicalRuleCounts.put(parent, childMap);
        }

        if (!childMap.containsKey(child)) {
            lexicalRules++;
        }

        childMap.put(child, childMap.getFloat(child) + increment);
        totalLexicalRuleCounts += increment;
    }

    void incrementSentenceInitialCount(final String child, final float increment) {
        sentenceInitialWordCounts.put(child, sentenceInitialWordCounts.getFloat(child) + increment);
    }

    @Override
    public final double binaryRuleObservations(final String parent, final String leftChild, final String rightChild) {

        final HashMap<String, Object2FloatMap<String>> leftChildMap = binaryRuleCounts.get(parent);
        if (leftChildMap == null) {
            return 0;
        }

        final Object2FloatMap<String> rightChildMap = leftChildMap.get(leftChild);
        if (rightChildMap == null) {
            return 0;
        }

        return rightChildMap.getFloat(rightChild);
    }

    @Override
    public final double unaryRuleObservations(final String parent, final String child) {

        final Object2FloatMap<String> childMap = unaryRuleCounts.get(parent);
        if (childMap == null) {
            return 0;
        }

        return childMap.getFloat(child);
    }

    @Override
    public final double lexicalRuleObservations(final String parent, final String child) {

        final Object2FloatMap<String> childMap = lexicalRuleCounts.get(parent);
        if (childMap == null) {
            return 0;
        }

        return childMap.getFloat(child);
    }

    /**
     * @param comparator Sort order for the induced vocabulary. If null, non-terminals will be ordered in the order of
     *            their observation, starting with the start symbol.
     * 
     * @return A {@link MutableEnumeration} induced from the observed non-terminals, sorted according to the supplied
     *         comparator.
     */
    public final SplitVocabulary induceVocabulary(final Comparator<String> comparator) {
        final ArrayList<String> nts = new ArrayList<String>(observedNonTerminals);
        if (comparator != null) {
            Collections.sort(nts, comparator);
        }

        return new SplitVocabulary(nts);
    }

    public final MutableEnumeration<String> induceLexicon() {
        return new MutableEnumeration<String>(new ObjectRBTreeSet<String>(lexicalEntryOccurrences.keySet()));
    }

    /**
     * Constructs a {@link FractionalCountGrammar} based on this {@link StringCountGrammar}, inducing a vocabulary
     * sorted by binary parent count.
     */
    public FractionalCountGrammar toFractionalCountGrammar() {
        return toFractionalCountGrammar(0, 0);
    }

    /**
     * Constructs a {@link FractionalCountGrammar} based on this {@link StringCountGrammar}, inducing a vocabulary
     * sorted by binary parent count.
     * 
     * @param rareWordThreshold
     */
    public FractionalCountGrammar toFractionalCountGrammar(final int uncommonWordThreshold, final int rareWordThreshold) {
        final SplitVocabulary vocabulary = induceVocabulary(binaryParentCountComparator());

        final MutableEnumeration<String> lexicon = induceLexicon();
        final FractionalCountGrammar fcg = new FractionalCountGrammar(vocabulary, lexicon, null, wordCounts(lexicon),
                sentenceInitialWordCounts(lexicon), uncommonWordThreshold, rareWordThreshold);

        for (final String parent : binaryRuleCounts.keySet()) {
            final HashMap<String, Object2FloatMap<String>> leftChildMap = binaryRuleCounts.get(parent);

            for (final String leftChild : leftChildMap.keySet()) {
                final Object2FloatMap<String> rightChildMap = leftChildMap.get(leftChild);

                for (final String rightChild : rightChildMap.keySet()) {
                    fcg.incrementBinaryCount((short) vocabulary.getIndex(parent),
                            (short) vocabulary.getIndex(leftChild), (short) vocabulary.getIndex(rightChild),
                            rightChildMap.getFloat(rightChild));
                }
            }
        }

        for (final String parent : unaryRuleCounts.keySet()) {
            final Object2FloatMap<String> childMap = unaryRuleCounts.get(parent);

            for (final String child : childMap.keySet()) {
                fcg.incrementUnaryCount((short) vocabulary.getIndex(parent), (short) vocabulary.getIndex(child),
                        childMap.getFloat(child));
            }
        }

        for (final String parent : lexicalRuleCounts.keySet()) {
            final Object2FloatMap<String> childMap = lexicalRuleCounts.get(parent);

            for (final String child : childMap.keySet()) {
                fcg.incrementLexicalCount((short) vocabulary.getIndex(parent), lexicon.getIndex(child),
                        childMap.getFloat(child));
            }
        }

        return fcg;
    }

    public ArrayList<Production> binaryProductions(final MutableEnumeration<String> vocabulary) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        // Iterate over mapped parents and children so the production list is in the same order as the
        // vocabulary
        for (short parent = 0; parent < vocabulary.size(); parent++) {
            final String sParent = vocabulary.getSymbol(parent);
            if (!binaryRuleCounts.containsKey(sParent)) {
                continue;
            }

            final HashMap<String, Object2FloatMap<String>> leftChildMap = binaryRuleCounts.get(sParent);

            for (short leftChild = 0; leftChild < vocabulary.size(); leftChild++) {
                final String sLeftChild = vocabulary.getSymbol(leftChild);
                if (!leftChildMap.containsKey(sLeftChild)) {
                    continue;
                }

                final Object2FloatMap<String> rightChildMap = leftChildMap.get(sLeftChild);

                for (short rightChild = 0; rightChild < vocabulary.size(); rightChild++) {
                    final String sRightChild = vocabulary.getSymbol(rightChild);
                    if (!rightChildMap.containsKey(sRightChild)) {
                        continue;
                    }

                    final float probability = (float) Math.log(binaryRuleObservations(sParent, sLeftChild, sRightChild)
                            * 1.0 / observations(sParent));
                    prods.add(new Production(parent, leftChild, rightChild, probability, vocabulary, null));
                }
            }
        }

        return prods;
    }

    public ArrayList<Production> unaryProductions(final MutableEnumeration<String> vocabulary) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        // Iterate over mapped parents and children so the production list is in the same order as the
        // vocabulary
        for (short parent = 0; parent < vocabulary.size(); parent++) {
            final String sParent = vocabulary.getSymbol(parent);
            if (!unaryRuleCounts.containsKey(sParent)) {
                continue;
            }

            final Object2FloatMap<String> childMap = unaryRuleCounts.get(sParent);

            for (short child = 0; child < vocabulary.size(); child++) {
                final String sChild = vocabulary.getSymbol(child);
                if (!childMap.containsKey(sChild)) {
                    continue;
                }

                final float probability = (float) Math.log(unaryRuleObservations(sParent, sChild) * 1.0
                        / observations(sParent));
                prods.add(new Production(parent, child, probability, true, vocabulary, null));
            }
        }

        return prods;
    }

    public ArrayList<Production> lexicalProductions(final MutableEnumeration<String> vocabulary) {
        return lexicalProductions(vocabulary, induceLexicon());
    }

    public ArrayList<Production> lexicalProductions(final MutableEnumeration<String> vocabulary,
            final MutableEnumeration<String> lexicon) {

        final ArrayList<Production> prods = new ArrayList<Production>();

        // Iterate over mapped parents and children so the production list is in the same order as the
        // vocabulary
        for (short parent = 0; parent < vocabulary.size(); parent++) {
            final String sParent = vocabulary.getSymbol(parent);
            if (!lexicalRuleCounts.containsKey(sParent)) {
                continue;
            }

            final Object2FloatMap<String> childMap = lexicalRuleCounts.get(sParent);

            for (int child = 0; child < lexicon.size(); child++) {
                final String sChild = lexicon.getSymbol(child);
                if (!childMap.containsKey(sChild)) {
                    continue;
                }

                final float probability = (float) Math.log(lexicalRuleObservations(sParent, sChild) * 1.0
                        / observations(sParent));
                prods.add(new Production(parent, child, probability, true, vocabulary, lexicon));
            }
        }

        return prods;
    }

    public Int2IntOpenHashMap wordCounts(final MutableEnumeration<String> lexicon) {

        final Int2IntOpenHashMap wordCounts = new Int2IntOpenHashMap();
        wordCounts.defaultReturnValue(0);

        for (final String parent : lexicalRuleCounts.keySet()) {

            final Object2FloatMap<String> childMap = lexicalRuleCounts.get(parent);

            for (final String word : childMap.keySet()) {
                final int index = lexicon.getIndex(word);
                wordCounts.addTo(index, Math.round(childMap.getFloat(word)));
            }
        }
        return wordCounts;
    }

    public Int2IntOpenHashMap sentenceInitialWordCounts(final MutableEnumeration<String> lexicon) {

        final Int2IntOpenHashMap wordCounts = new Int2IntOpenHashMap();
        wordCounts.defaultReturnValue(0);

        for (final String word : sentenceInitialWordCounts.keySet()) {
            final int index = lexicon.getIndex(word);
            wordCounts.addTo(index, Math.round(sentenceInitialWordCounts.getFloat(word)));
        }
        return wordCounts;
    }

    /**
     * @return a comparator which orders Strings based on the number of times each was observed as a binary parent. The
     *         most frequent parents are ordered earlier, with the exception of the start symbol, which is always first.
     */
    public Comparator<String> binaryParentCountComparator() {

        return new Comparator<String>() {

            @Override
            public int compare(final String o1, final String o2) {
                if (o1.equals(startSymbol)) {
                    return o2.equals(startSymbol) ? 0 : -1;
                } else if (o2.equals(startSymbol)) {
                    return 1;
                }

                final float count1 = binaryParentCounts.getFloat(o1);
                final float count2 = binaryParentCounts.getFloat(o2);
                if (count1 > count2) {
                    return -1;
                } else if (count1 < count2) {
                    return 1;
                }
                return 0;
            }
        };
    }

    @Override
    public final int totalRules() {
        return binaryRules() + unaryRules() + lexicalRules();
    }

    @Override
    public int binaryRules() {
        return binaryRules;
    }

    @Override
    public int unaryRules() {
        return unaryRules;
    }

    @Override
    public int lexicalRules() {
        return lexicalRules;
    }

    public float totalRuleCounts() {
        return totalBinaryRuleCounts + totalUnaryRuleCounts + totalLexicalRuleCounts;
    }

    @Override
    public final double observations(final String parent) {
        int count = 0;

        if (binaryRuleCounts.containsKey(parent)) {
            count += binaryParentCounts.getFloat(parent);
        }

        if (unaryRuleCounts.containsKey(parent)) {
            count += unaryParentCounts.getFloat(parent);
        }

        if (lexicalRuleCounts.containsKey(parent)) {
            count += lexicalParentCounts.getFloat(parent);
        }

        return count;
    }
}
