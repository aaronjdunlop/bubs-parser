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
package edu.ohsu.cslu.parser.spmv;

import java.io.IOException;
import java.io.Reader;

import edu.ohsu.cslu.grammar.CsrSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.DecisionTreeTokenClassifier;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar.PackingFunction;
import edu.ohsu.cslu.parser.ParserDriver;

/**
 * Tests FOM-pruned parsing.
 * 
 * @author Aaron Dunlop
 * @since Mar 9, 2011
 */
public class TestPrunedCsrSpmvParser extends PrunedSparseMatrixParserTestCase<CsrSparseMatrixGrammar> {

    @Override
    protected CsrSparseMatrixGrammar createGrammar(final Reader grammarReader,
            final Class<? extends PackingFunction> packingFunctionClass) throws IOException {
        return new CsrSparseMatrixGrammar(grammarReader, new DecisionTreeTokenClassifier(), packingFunctionClass);
    }

    @Override
    protected PackedArraySpmvParser<CsrSparseMatrixGrammar> createParser(final ParserDriver opts,
            final CsrSparseMatrixGrammar grammar) {
        return new CsrSpmvParser(opts, grammar);
    }
}
