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

package edu.ohsu.cslu.perceptron;

import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.datastructs.vectors.LargeSparseBitVector;
import edu.ohsu.cslu.datastructs.vectors.SparseBitVector;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.SymbolSet;

/**
 * Extracts features for POS tagging
 * 
 * We support:
 * 
 * <pre>
 * tm3    Tag i-3
 * tm2    Tag i-2
 * tm1    Previous tag
 * 
 * wm2    Word i-2
 * wm1    Previous word (i-1)
 * w      Word
 * wp1    Next word (i+1)
 * wp2    Word i+2
 * 
 * um2    Unknown word token i-2
 * um1    Unknown word token (i-1)
 * u      Unknown word token
 * up1    Unknown word token (i+1)
 * up2    Unknown word token i+2
 * </pre>
 * 
 * Unknown word features use the standard Berkeley UNK classes
 */
public class TaggerFeatureExtractor extends FeatureExtractor<TagSequence> {

    private static final long serialVersionUID = 1L;

    final TaggerFeatureExtractor.TemplateElement[][] templates;
    final long[] featureOffsets;

    final SymbolSet<String> lexicon;
    final SymbolSet<String> tags;

    final int nullToken, nullTag;
    final int lexiconSize, tagSetSize, unkClassSetSize;
    final long featureVectorLength;

    /**
     * Constructs a {@link FeatureExtractor} using the specified feature templates
     * 
     * @param featureTemplates
     * @param lexicon
     * @param unkClassSet
     * @param tagSet
     */
    public TaggerFeatureExtractor(final String featureTemplates, final SymbolSet<String> lexicon,
            final SymbolSet<String> unkClassSet, final SymbolSet<String> tagSet) {

        this.lexicon = lexicon;
        this.lexiconSize = lexicon.size();
        this.nullToken = lexicon.getIndex(Grammar.nullSymbolStr);

        this.tags = tagSet;
        this.tagSetSize = tags.size();
        this.unkClassSetSize = unkClassSet.size();
        this.nullTag = tags.getIndex(Grammar.nullSymbolStr);

        final String[] templateStrings = featureTemplates.split(",");
        this.templates = new TaggerFeatureExtractor.TemplateElement[templateStrings.length][];
        this.featureOffsets = new long[this.templates.length];

        for (int i = 0; i < featureOffsets.length; i++) {
            templates[i] = template(templateStrings[i]);
        }

        for (int i = 1; i < featureOffsets.length; i++) {
            featureOffsets[i] = featureOffsets[i - 1] + templateSize(templates[i - 1]);
            // Blow up if we wrap around Long.MAX_VALUE
            if (featureOffsets[i] < 0) {
                throw new IllegalArgumentException("Feature set too large. Features limited to " + Long.MAX_VALUE);
            }
        }

        this.featureVectorLength = featureOffsets[featureOffsets.length - 1]
                + templateSize(templates[templates.length - 1]);
        if (featureVectorLength < 0) {
            throw new IllegalArgumentException("Feature set too large. Features limited to " + Long.MAX_VALUE);
        }
    }

    @Override
    public int templateCount() {
        return templates.length;
    }

    private TaggerFeatureExtractor.TemplateElement[] template(final String templateString) {
        final String[] split = templateString.split("_");
        final TaggerFeatureExtractor.TemplateElement[] template = new TaggerFeatureExtractor.TemplateElement[split.length];
        for (int i = 0; i < split.length; i++) {
            template[i] = TemplateElement.valueOf(TaggerFeatureExtractor.TemplateElement.class, split[i]);
        }
        return template;
    }

    private long templateSize(final TaggerFeatureExtractor.TemplateElement[] template) {
        long size = 1;
        for (int i = 0; i < template.length; i++) {
            switch (template[i]) {

            case tm1:
            case tm2:
            case tm3:
                size *= tagSetSize;
                break;

            case um2:
            case um1:
            case u:
            case up1:
            case up2:
                size *= unkClassSetSize;
                break;

            case wm2:
            case wm1:
            case w:
            case wp1:
            case wp2:
                size *= lexiconSize;
                break;
            }

            if (size < 0) {
                throw new IllegalArgumentException("Feature set too large. Features limited to " + Long.MAX_VALUE);
            }
        }
        return size;
    }

    @Override
    public long vectorLength() {
        return featureVectorLength;
    }

    @Override
    public BitVector featureVector(final TagSequence sequence, final int position) {

        final long[] featureIndices = new long[templates.length];

        for (int i = 0; i < templates.length; i++) {
            long feature = 0;
            final TaggerFeatureExtractor.TemplateElement[] template = templates[i];
            for (int j = 0; j < template.length; j++) {
                final TaggerFeatureExtractor.TemplateElement t = template[j];
                final int index = position + t.offset;

                switch (t) {

                case tm1:
                case tm2:
                case tm3:
                    feature *= tagSetSize;
                    feature += ((index < 0 || index >= sequence.predictedTags.length) ? nullTag
                            : sequence.predictedTags[index]);
                    break;

                case um2:
                case um1:
                case u:
                case up1:
                case up2:
                    feature *= unkClassSetSize;
                    feature += ((index < 0 || index >= sequence.mappedTokens.length) ? nullToken
                            : sequence.mappedUnkSymbols[index]);
                    break;

                case wm2:
                case wm1:
                case w:
                case wp1:
                case wp2:
                    feature *= lexiconSize;
                    feature += ((index < 0 || index >= sequence.mappedTokens.length) ? nullToken
                            : sequence.mappedTokens[index]);
                    break;
                }
            }
            final long featureIndex = featureOffsets[i] + feature;
            assert featureIndex >= 0 && featureIndex < featureVectorLength;
            featureIndices[i] = featureIndex;
        }

        return featureVectorLength > Integer.MAX_VALUE ? new LargeSparseBitVector(featureVectorLength, featureIndices)
                : new SparseBitVector(featureVectorLength, featureIndices);
    }

    private enum TemplateElement {
        tm3(-3), // Tag i-3
        tm2(-2), // Tag i-2
        tm1(-1), // Previous tag

        wm2(-2), // Word i-2
        wm1(-1), // Previous word (i-1)
        w(0), // Word
        wp1(1), // Next word (i+1)
        wp2(2), // Word i+2

        um2(-2), // Unknown word token i-2
        um1(-1), // Unknown word token (i-1)
        u(0), // Unknown word token
        up1(1), // Unknown word token (i+1)
        up2(2); // Unknown word token i+2

        final int offset;

        private TemplateElement(final int offset) {
            this.offset = offset;
        }
    }
}