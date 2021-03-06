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
package edu.ohsu.cslu.parser;

import static org.junit.Assert.assertEquals;

import org.cjunit.FilteredRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import edu.ohsu.cslu.grammar.GrammarFormatType;

/**
 * Unit tests for {@link Parser} class.
 * 
 * @author Aaron Dunlop
 */
@RunWith(FilteredRunner.class)
public class TestParser {

    @Test
    public void testUnfactor() throws Exception {
        assertEquals("(ROOT (NP (: --) (NNP C.E.) (NNP Friedman) (. .)))", TreeTools.unfactor(
                "(ROOT_0 (NP_31 (@NP_29 (@NP_40 (:_3 --) (NNP_0 C.E.)) (NNP_9 Friedman)) (._3 .)))",
                GrammarFormatType.Berkeley));

        assertEquals(
                "(ROOT (S (NP (NN Trouble)) (VP (VBZ is) (, ,) (SBAR (S (NP (PRP she)) (VP (VBZ has) (VP (VBN lost) ("
                        + "NP (PRP it)) (ADVP (RB just) (RB as) (RB quickly))))))) (. .)))", TreeTools.unfactor(
                        "(ROOT_0 (S_0 (@S_24 (NP_23 (NN_26 Trouble)) (VP_32 (@VP_10 (VBZ_17 is) (,_0 ,))"
                                + " (SBAR_1 (S_5 (NP_36 (PRP_2 she)) (VP_34 (VBZ_16 has) (VP_11 (@VP_28"
                                + " (VBN_23 lost) (NP_37 (PRP_1 it))) (ADVP_1 (@ADVP_0 (RB_31 just)"
                                + " (RB_32 as)) (RB_2 quickly)))))))) (._3 .)))", GrammarFormatType.Berkeley));

        assertEquals("(TOP (S (NP (NP (JJ Little) (NN chance)) (PP (IN that) (NP (NNP Shane)"
                + " (NNP Longman)))) (VP (AUX is) (VP (VBG going) (S (VP (TO to) (VP (VB recoup) "
                + "(NP (NN today))))))) (. .)))", TreeTools.unfactor(
                "(TOP (S^<TOP> (S|<NP-VP>^<TOP> (NP^<S> (NP^<NP> (JJ Little) (NN chance))"
                        + " (PP^<NP> (IN that) (NP^<PP> (NNP Shane) (NNP Longman))))"
                        + " (VP^<S> (AUX is) (VP^<VP> (VBG going) (S^<VP> (VP^<S> (TO to)"
                        + " (VP^<VP> (VB recoup) (NP^<VP> (NN today)))))))) (. .)))", GrammarFormatType.CSLU));

        assertEquals(
                "(S (NP (JJ R) (: _) (-LRB- -LRB-) (NN n) (-RRB- -RRB-)) (VP (VP (VBZ represents) (NP (NP (DT the) (NN number)) (PP (IN of) (NP (NNS documents)))))))",
                TreeTools
                        .unfactor(
                                "(S_0 (NP_2 (JJ_0 R) (@NP_1 (:_1 _) (@NP_2 (-LRB-_1 -LRB-) (@NP_3 (NN_1 n) (-RRB-_0 -RRB-))))) (VP_0 (VP_0 (VBZ_1 represents) (NP_2 (NP_1 (DT_0 the) (NN_1 number)) (PP_2 (IN_3 of) (NP_3 (NNS_1 documents)))))))",
                                GrammarFormatType.Berkeley));
    }
}
