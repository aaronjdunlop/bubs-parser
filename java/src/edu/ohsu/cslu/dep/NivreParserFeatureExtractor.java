/*
 * Copyright 2010-2012 Aaron Dunlop and Nathan Bodenstab
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
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */

package edu.ohsu.cslu.dep;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

import edu.ohsu.cslu.datastructs.vectors.SparseBitVector;
import edu.ohsu.cslu.datastructs.vectors.Vector;
import edu.ohsu.cslu.dep.DependencyGraph.Arc;
import edu.ohsu.cslu.grammar.SymbolSet;
import edu.ohsu.cslu.grammar.Tokenizer;
import edu.ohsu.cslu.perceptron.FeatureExtractor;

/**
 * Extracts features for move-classification in Nivre-style dependency parsing from the current state of such a parser
 * (stack, arcs, etc.)
 * 
 * @author Aaron Dunlop
 */
public class NivreParserFeatureExtractor extends FeatureExtractor<NivreParserContext> {

    private static final long serialVersionUID = 1L;

    public final static String NULL = "<null>";

    final static int DISTANCE_1 = 0;
    final static int DISTANCE_2 = DISTANCE_1 + 1;
    final static int DISTANCE_3 = DISTANCE_2 + 1;
    final static int DISTANCE_45 = DISTANCE_3 + 1;
    final static int DISTANCE_6 = DISTANCE_45 + 1;
    final static int DISTANCE_BINS = 5;

    final TemplateElements[][] templates;
    final int[] featureOffsets;

    final SymbolSet<String> tokens;
    final SymbolSet<String> pos;
    final int nullPosTag, nullToken;
    final int tokenSetSize, posSetSize;
    final int featureVectorLength;

    public NivreParserFeatureExtractor(final SymbolSet<String> tokens, final SymbolSet<String> pos) {
        // Features:
        //
        // Previous word (on the stack), current word (top-of-stack), next word (not-yet-shifted),
        //
        // UNK symbol for each of those 3 words (in the same range as the tokens themselves)
        //
        // POS for each of those 3 words
        //
        // Start-of-string indicator for previous word
        //
        // End-of-string indicator for next word
        //
        // Previous POS + current POS
        // Previous POS + current word
        // Previous word + current POS
        //
        // Distance between the top two words on the stack (the two under consideration for reduce operations)
        // Binned: 1, 2, 3, 4-5, 6+ words
        //
        this("sw2,sw1,iw1,st2,st1,it1,it1_it2,iw1_it2,it1_iw2,d", tokens, pos);
    }

    /**
     * Supported template abbreviations:
     * 
     * sw3, sw2, sw1, iw1, iw1, iw3, iw4
     * 
     * st3, st2, st1, it1, it2, it3, it4
     * 
     * d
     * 
     * @param featureTemplates
     * @param tokens
     * @param pos
     */
    public NivreParserFeatureExtractor(final String featureTemplates, final SymbolSet<String> tokens,
            final SymbolSet<String> pos) {

        this.tokens = tokens;
        this.tokenSetSize = tokens.size();
        this.nullToken = tokens.getIndex(NULL);
        this.pos = pos;
        this.posSetSize = pos.size();
        this.nullPosTag = pos.getIndex(NULL);

        final String[] templateStrings = featureTemplates.split(",");
        this.templates = new TemplateElements[templateStrings.length][];
        this.featureOffsets = new int[this.templates.length];

        for (int i = 0; i < featureOffsets.length; i++) {
            templates[i] = template(templateStrings[i]);
        }

        for (int i = 1; i < featureOffsets.length; i++) {
            featureOffsets[i] = featureOffsets[i - 1] + templateSize(templates[i - 1]);
        }
        this.featureVectorLength = featureOffsets[featureOffsets.length - 1]
                + templateSize(templates[templates.length - 1]);
    }

    private TemplateElements[] template(final String templateString) {
        final String[] split = templateString.split("_");
        final TemplateElements[] template = new TemplateElements[split.length];
        for (int i = 0; i < split.length; i++) {
            template[i] = TemplateElements.valueOf(TemplateElements.class, split[i]);
        }
        return template;
    }

    private int templateSize(final TemplateElements[] template) {
        int size = 1;
        for (int i = 0; i < template.length; i++) {
            switch (template[i]) {
            case st3:
            case st2:
            case st1:
            case it1:
            case it2:
            case it3:
            case it4:
                size *= posSetSize;
                break;

            case sw3:
            case sw2:
            case sw1:
            case iw1:
            case iw2:
            case iw3:
            case iw4:
                size *= tokenSetSize;
                break;

            case d:
                size *= DISTANCE_BINS;
                break;

            }
        }
        return size;
    }

    @Override
    public long featureCount() {
        return featureVectorLength;
    }

    @Override
    public SparseBitVector forwardFeatureVector(final NivreParserContext source, final int tokenIndex) {

        final IntArrayList featureIndices = new IntArrayList();

        // TODO Handle UNKs
        for (int i = 0; i < templates.length; i++) {
            try {
                int feature = 0;
                final TemplateElements[] template = templates[i];
                for (int j = 0; j < template.length; j++) {
                    switch (template[j]) {
                    case st3:
                    case st2:
                    case st1:
                        feature *= posSetSize;
                        feature += tag(source.stack, template[j].index);
                        break;
                    case it1:
                    case it2:
                    case it3:
                    case it4:
                        feature *= posSetSize;
                        feature += tag(source.arcs, tokenIndex + template[j].index);
                        break;

                    case sw3:
                    case sw2:
                    case sw1:
                        feature *= tokenSetSize;
                        feature += token(source.stack, template[j].index);
                        break;
                    case iw1:
                    case iw2:
                    case iw3:
                    case iw4:
                        feature *= tokenSetSize;
                        feature += token(source.arcs, tokenIndex + template[j].index);
                        break;

                    case d:
                        if (source.stack.size() < 2) {
                            throw new InvalidFeatureException();
                        }
                        // Previous word on the stack
                        final Arc previousWord = source.stack.get(1);
                        // Top word on the stack
                        final Arc currentWord = source.stack.get(0);

                        // Distance between top two words on stack
                        switch (currentWord.index - previousWord.index) {
                        case 1:
                            feature += DISTANCE_1;
                            break;
                        case 2:
                            feature += DISTANCE_2;
                            break;
                        case 3:
                            feature += DISTANCE_3;
                            break;
                        case 4:
                        case 5:
                            feature += DISTANCE_45;
                            break;
                        default:
                            feature += DISTANCE_6;
                            break;
                        }
                        if (j < template.length - 1) {
                            feature *= DISTANCE_BINS;
                        }
                        break;
                    }

                }
                featureIndices.add(featureOffsets[i] + feature);
            } catch (final InvalidFeatureException e) {
                // Just skip this feature
            }
        }

        return new SparseBitVector(featureVectorLength, featureIndices.toIntArray());
    }

    /**
     * @param stack
     * @param index
     * @return
     */
    private int token(final List<Arc> stack, final int index) {
        if (index < 0 || index >= stack.size()) {
            return nullToken;
        }
        return tokens.getIndex(stack.get(index).token);
    }

    /**
     * @param arcs
     * @param i
     * @return
     */
    private int token(final Arc[] arcs, final int i) {
        if (i < 0 || i >= arcs.length) {
            return nullToken;
        }
        return tokens.getIndex(arcs[i].token);
    }

    /**
     * @param arcs
     * @param i
     * @return
     */
    private int tag(final List<Arc> stack, final int index) {
        if (index < 0 || index >= stack.size()) {
            return nullPosTag;
        }
        return pos.getIndex(stack.get(index).pos);
    }

    /**
     * @param arcs
     * @param i
     * @return
     */
    private int tag(final Arc[] arcs, final int i) {
        if (i < 0 || i >= arcs.length) {
            return nullPosTag;
        }
        return pos.getIndex(arcs[i].pos);
    }

    /**
     * @param arcs
     * @param i
     * @return
     */
    private int unk(final Arc[] arcs, final int i) {
        if (i < 0 || i >= arcs.length) {
            return nullToken;
        }
        return tokens.getIndex(Tokenizer.berkeleyGetSignature(arcs[i].token, i == 0, tokens));
    }

    @Override
    public Vector forwardFeatureVector(final NivreParserContext source, final int tokenIndex, final float[] tagScores) {
        return null;
    }

    private enum TemplateElements {
        sw3(2),
        sw2(1),
        sw1(0),
        iw1(0),
        iw2(1),
        iw3(2),
        iw4(3),
        st3(2),
        st2(1),
        st1(0),
        it1(0),
        it2(1),
        it3(2),
        it4(3),
        d(-1);

        final int index;

        private TemplateElements(final int index) {
            this.index = index;
        }
    }

    private class InvalidFeatureException extends Exception {

        private static final long serialVersionUID = 1L;
    }
}
