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
package edu.ohsu.cslu.datastructs.vectors;

import static org.junit.Assert.assertArrayEquals;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.Arrays;

import org.junit.Test;

/**
 * Unit tests for immutable sparse {@link BitVector} implementations
 * 
 * @author Aaron Dunlop
 */
public abstract class ImmutableBitVectorTestCase<V extends BitVector> extends BitVectorTestCase<V> {

    /**
     * Verifies that the constructor corrects mis-ordered elements
     * 
     * @throws Exception
     */
    @Test
    public void testConstructors() throws Exception {
        final SparseBitVector v = new SparseBitVector(new int[] { 8, 5, 7, 3, });
        assertArrayEquals(new int[] { 3, 5, 7, 8 }, v.values());
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testSet() throws Exception {
        sampleVector.set(2, true);
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testSetAdd() {
        ((BitVector) sampleVector).add(3);
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testArrayAddAll() {
        ((BitVector) sampleVector).addAll(new int[] { 1, 3 });
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testIntSetAddAll() {
        ((BitVector) sampleVector).addAll(new IntOpenHashSet(Arrays.asList(1, 3)));
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testRemove() {
        ((BitVector) sampleVector).remove(2);
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testArrayRemoveAll() {
        ((BitVector) sampleVector).removeAll(new int[] { 2, 3 });
    }

    @Override
    @Test(expected = UnsupportedOperationException.class)
    public void testIntSetRemoveAll() {
        ((BitVector) sampleVector).removeAll(new IntOpenHashSet(Arrays.asList(1, 3)));
    }

}
