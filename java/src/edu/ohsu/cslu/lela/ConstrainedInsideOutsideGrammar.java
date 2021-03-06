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

import edu.ohsu.cslu.grammar.DecisionTreeTokenClassifier;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.InsideOutsideCscSparseMatrixGrammar;

/**
 * Grammar class for split-merge grammar learning. References a parent grammar, based on the parent (pre-split)
 * vocabulary.
 * 
 * @author Aaron Dunlop
 */
public class ConstrainedInsideOutsideGrammar extends InsideOutsideCscSparseMatrixGrammar {

    private static final long serialVersionUID = 1L;

    public ConstrainedInsideOutsideGrammar(final FractionalCountGrammar countGrammar,
            final GrammarFormatType grammarFormat, final Class<? extends PackingFunction> functionClass) {

        super(countGrammar.binaryProductions(Float.NEGATIVE_INFINITY), countGrammar
                .unaryProductions(Float.NEGATIVE_INFINITY), countGrammar.lexicalProductions(Float.NEGATIVE_INFINITY),
                countGrammar.vocabulary, countGrammar.lexicon, grammarFormat, new DecisionTreeTokenClassifier(),
                functionClass, true);
    }

    public SplitVocabulary vocabulary() {
        return (SplitVocabulary) nonTermSet;
    }
}
