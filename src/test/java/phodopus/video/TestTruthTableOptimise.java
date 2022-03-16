/* Copyright © 2022 Matthew Wilson
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0
 * which is available at:
 * http://www.eclipse.org/legal/epl-2.0
 * or the GNU General Public License v3.0 or later
 * which is available at:
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-3.0-or-later
 */
package phodopus.video;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.collect.ImmutableList;

@RunWith( Parameterized.class )
public final class TestTruthTableOptimise
{
    private final TruthTable table;

    public TestTruthTableOptimise( TruthTable table )
    {
        this.table = Objects.requireNonNull( table );
    }

    @Parameters( name = "{index}: {0}" )
    public static List< TruthTable > parameters()
    {
        return IntStream.rangeClosed( 1, 24 ).mapToObj( TestTruthTableOptimise::makeTables ).flatMap( List::stream ).toList();
    }

    @Test
    public void optimise()
    {
        Expression expression = this.table.optimise();

        int size = 1 << this.table.bitCount();
        for ( int i = 0; i < size; i++ )
        {
            Assert.assertEquals( this.table.table().get( i ), expression.evaluate( i ) );
        }
    }

    private static List< TruthTable > makeTables( int bitCount )
    {
        TruthTable allFalse = TruthTable.create( "all-false-" + bitCount,
                                                 bitCount,
                                                 i -> false );
        TruthTable allTrue = TruthTable.create( "all-true-" + bitCount,
                                                 bitCount,
                                                 i -> true );
        TruthTable random = TruthTable.create( "random-" + bitCount,
                                               bitCount,
                                               i -> ( ( i * 0x5DEECE66DL + 0xB ) & 1024 ) == 0 );
        return ImmutableList.of( allFalse, allTrue, random );
    }
}
