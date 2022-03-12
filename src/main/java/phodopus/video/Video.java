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

import static phodopus.video.Util.bitRange;
import static phodopus.video.Util.concat;
import static phodopus.video.Util.isBetween;
import static phodopus.video.Util.isEqual;
import static phodopus.video.Util.isSet;
import static phodopus.video.Util.optimise;

import java.util.List;

import com.google.common.collect.ImmutableList;

import phodopus.video.Chip.BitFormatter;
import phodopus.video.Simulation.State;

public final class Video
{
    public static void main( String[] args )
    {
        Chip vcounter = vcounter();
        Chip hcounter = hcounter();
        Chip hflags = hflags();
        Chip vflags = vflags();
        Chip addr0 = addr0();
        Chip addr1 = addr1();
        Chip pixelconv = pixelconv();

        ImmutableList< Chip > chips = ImmutableList.of( hcounter, vcounter, hflags, vflags, addr0, addr1, pixelconv );
        dumpAllExpressions( chips );

        Simulation simulation = Simulation.create( chips );
        runFullSimulation( simulation );
//        runAddressSimulation( simulation );
    }

    private static void dumpAllExpressions( ImmutableList< Chip > chips )
    {
        for ( Chip chip : chips )
        {
            System.out.println( chip.name() );
            System.out.println(  );
            chip.dumpExpressions();
            System.out.println(  );
            System.out.println( "--------------" );
            System.out.println(  );
        }
    }

    private static void runFullSimulation( Simulation simulation )
    {
        State state = simulation.zeroState().with( "Clear", true );

        for ( int i = 0; i < 525 * 400 + 130 * 400; i++ )
        {
            state = state.next();
            if ( state.number( "H0", 10 ) == 0 )
            {
                System.out.println(  );
            }

            System.out.printf( "%04d %03d %02x%02x %s %s %s %s %s %s %s %s %s %s %s %s %s %s%n",
                               state.number( "V0", 10 ),
                               state.number( "H0", 9 ),
                               state.number( "A8", 7 ),
                               state.number( "A0", 8 ),
                               state.formattedFlag( "HE399" ),
                               state.formattedFlag( "HS" ),
                               state.formattedFlag( "VIS" ),
                               state.formattedFlag( "RE" ),
                               state.formattedFlag( "VS" ),
                               state.formattedFlag( "VVIS" ),
                               state.formattedFlag( "VE524" ),
                               state.formattedFlag( "INCADDR" ),
                               state.formattedFlag( "REPLINE" ),
                               state.formattedFlag( "REPLINE2" ),
                               state.formattedFlag( "SETADDR" ),
                               state.formattedFlag( "AC" ),
                               state.formattedFlag( "A14INC" ),
                               state.formattedFlag( "A14REP" ) );
        }
    }

    private static void runAddressSimulation( Simulation simulation )
    {
        State state = simulation.zeroState().with( "Clear", true );

        int oldAddress = 0;
        int oldH = 0;
        int oldV = 0;
        boolean printedContinuousMarker = false;

        for ( int i = 0; i < 525 * 400 * 2 + 10; i++ )
        {
            state = state.next();
            int address = state.number( "A0", 8 ) | ( state.number( "A8", 7 ) << 8 );
            int h = state.number( "H0", 9 );
            int v = state.number( "V0", 10 );
            if ( address != oldAddress || ( i % ( 525 * 400 ) == 0 ) )
            {
                if ( i >= 525 * 400 )
                {
                    boolean continuous = ( h - oldH ) == 2 && ( v == oldV );
                    if ( !continuous || !printedContinuousMarker )
                    {
                        if ( printedContinuousMarker )
                        {
                            System.out.printf( "%03x %03x %04x%n",
                                               oldV,
                                               oldH,
                                               oldAddress );
                        }
                        System.out.printf( "%03x %03x %04x%n",
                                           v,
                                           h,
                                           address );
                        printedContinuousMarker = false;
                    }

                    if ( continuous && !printedContinuousMarker )
                    {
                        System.out.println( "..." );
                        printedContinuousMarker = true;
                    }
                }
                oldAddress = address;
                oldH = h;
                oldV = v;
            }
        }
    }

    private static int mangleAddress( int address )
    {
        int b6 = 1 << 6;
        int b7 = 1 << 7;
        return ( address & ~( b6 | b7 ) ) | ( ( address & b6 ) << 1 ) | ( ( address & b7 ) >>> 1 );
    }

    private static Chip hcounter()
    {
        List< String > inputs = ImmutableList.of( "Clear" );
        List< String > outputNames = concat( bitRange( "H", 9 ), "HE399" );

        List< TruthTable > tables = TruthTable
            .createMany( inputs,
                         outputNames,
                         i -> isSet( i, 9 ) || isSet( i, 10 ) ? 0 : ( ( ( i & 511 ) + 1 ) & 511 ) | ( i == 398 && !isSet( i, 10 ) ? 512 : 0 ) );
        List< Expression > outputs = optimise( tables );

        return new Chip( "hcounter",
                         inputs,
                         outputs,
                         new BitFormatter( "[0-8] [9,HE399]" ) );
    }

    private static Chip vcounter()
    {
        List< String > inputs = ImmutableList.of( "HE399", "VE524", "Clear" );
        List< String > outputNames = bitRange( "V", 10 );
        int he399 = outputNames.size();
        int ve524 = he399 + 1;
        int clear = ve524 + 1;

        List< TruthTable > tables = TruthTable
            .createMany( inputs,
                         outputNames,
                         i ->
        {
            if ( isSet( i, clear ) || ( isSet( i, ve524 ) && isSet( i, he399 ) ) )
            {
                return 0;
            }

            if ( !isSet( i, he399 ) )
            {
                // Don't increment.
                return i & 1023;
            }

            return ( ( i & 1023 ) + 1 ) & 1023;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "vcounter",
                         inputs,
                         outputs,
                         new BitFormatter( "[0-9]" ) );
    }

    private static Chip hflags()
    {
        List< String > outputNames = ImmutableList.of( "HS", "VIS", "RE", "INCADDR", "REPLINE", "REPLINE2" );
        // [5..13] [14] [15]
        List< String > inputs = concat( bitRange( "H", 9 ), ImmutableList.of( "HE399", "VVIS", "V0" ) );
        int h0 = outputNames.size();
        int he399 = h0 + 9;
        int vvis = he399 + 1;
        int v0 = vvis + 1;
        int bitCount = outputNames.size() + inputs.size();

        // H->8 to H->55 inclusive.
        TruthTable hs = TruthTable.create( "HS", bitCount, i -> !isBetween( i, h0, 511, 7, 55 ) );

        // VIS is just one cycle behind RE
        TruthTable vis = TruthTable.create( "VIS", bitCount, i -> isSet( i, 2 ) );

        // H->78 to H->397 inclusive, and VVIS.
        TruthTable re = TruthTable.create( "RE", bitCount, i -> isBetween( i, h0, 511, 77, 397 ) && isSet( i, vvis ) );

        // Incremented address needs to be visible when H->80, H->82, ..., H->398.
        // Therefore, INCADDR must be set when H->79, H->81, ..., H->397.
        // => INCADDR = RE * /H0
        TruthTable incaddr = TruthTable.create( "INCADDR", bitCount, i -> isSet( i, 2 ) && !isSet( i, h0 ) );

        // Adjusted address needs to be visible at any point before H->78-ish.
        // Therefore, H->0 (i.e. HE399) is fine.
        // Only repeat an even display line.
        TruthTable repline =
            TruthTable.create( "REPLINE",
                               bitCount,
                               i -> isSet( i, vvis ) && isSet( i, he399 ) && isSet( i, v0 ) );

        // Delayed copy of repline for addr2 and its magic.
        TruthTable repline2 =
            TruthTable.create( "REPLINE2",
                               bitCount,
                               i -> isSet( i, 4 ) );

        List< Expression > outputs = optimise( ImmutableList.of( hs, vis, re, incaddr, repline, repline2 ) );
        return new Chip( "hflags",
                         inputs,
                         outputs,
                         new BitFormatter( "[0,HS] [1,VIS] [2,RE]" ) );
    }

    private static Chip vflags()
    {
        List< String > outputNames = ImmutableList.of( "VVIS", "VS", "VE524", "SETADDR" );
        // [4..13] [14]
        List< String > inputs = concat( bitRange( "V", 10 ), ImmutableList.of( "HE399" ) );
        int v0 = outputNames.size();
        int he399 = v0 + 10;
        int bitCount = outputNames.size() + inputs.size();

        TruthTable vvis = TruthTable.create( "VVIS", bitCount, i ->
        {
            // V->125 (requires HE399 for change of line next cycle)
            if ( isEqual( i, v0, 1023, 124 ) && isSet( i, he399 ) )
            {
                return true;
            }
            // V->0 == VE524 was set (requires HE399 for change of line next cycle)
            if ( isSet( i, 2 ) && isSet( i, he399 ) )
            {
                return false;
            }

            // Preserve state.
            return isSet( i, 0 );
        } );

        TruthTable vs = TruthTable.create( "VS", bitCount, i ->
        {
            // V->50 (requires HE399 for change of line next cycle)
            if ( isEqual( i, v0, 1023, 49 ) && isSet( i, he399 ) )
            {
                return false;
            }
            // V->52 (requires HE399 for change of line next cycle)
            // or when it's reset (V<32)
            if ( isBetween( i, v0, 1023, 0, 32 ) || isEqual( i, v0, 1023, 51 ) && isSet( i, he399 ) )
            {
                return true;
            }

            // Preserve state.
            return isSet( i, 1 );
        } );

        // V=524
        TruthTable ve524 = TruthTable.create( "VE524", bitCount, i -> isEqual( i, v0, 1023, 524 ) );

        // V=124 (gives time for address to be changed by software after vsync)
        TruthTable setaddr = TruthTable.create( "SETADDR", bitCount, i -> isEqual( i, v0, 1023, 124 ) );

        List< Expression > outputs = optimise( ImmutableList.of( vvis, vs, ve524, setaddr ) );
        return new Chip( "vflags",
                         inputs,
                         outputs,
                         new BitFormatter( "[0,VVIS] [1,VIS] [2,RE], [3,SETADDR]" ) );
    }

    private static Chip addr0()
    {
        List< String > outputNames = concat( bitRange( "A", 8 ), "AC" );
        // [9..16] [17] [18] [19]
        List< String > inputs = concat( bitRange( "AS", 8 ), ImmutableList.of( "INCADDR", "REPLINE", "SETADDR" ) );
        int as0 = outputNames.size();
        int incaddr = as0 + 8;
        int repline = incaddr + 1;
        int setaddr = repline + 1;

        int mask = 255;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as0 ) & mask;
            }
            if ( isSet( i, repline ) )
            {
                // REPLINE
                int ac = ( i & mask ) < 160 ? 1 << 8 : 0;
                return ( ( i & mask ) - 160 ) & mask | ac;
            }

            int ac = ( i & mask ) == mask ? 1 << 8 : 0;
            if ( isSet( i, incaddr ) )
            {
                // INCADDR
                return ( ( i & mask ) + 1 ) & mask | ac;
            }
            // Don't change.
            return i & mask | ac;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "addr0",
                         inputs,
                         outputs,
                         new BitFormatter( "[x0-7] [8,AC]" ) );
    }

    private static Chip addr1()
    {
        List< String > outputNames = concat( bitRange( "A", 8, 7 ), ImmutableList.of( "A14INC", "A14REP" ) );
        List< String > inputs = concat( bitRange( "AS", 8, 7 ), ImmutableList.of( "INCADDR", "REPLINE2", "SETADDR", "AC" ) );
        int as8 = outputNames.size();
        int incaddr = as8 + 7;
        int repline2 = incaddr + 1;
        int setaddr = repline2 + 1;
        int ac = setaddr + 1;

        int mask = 127;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            // If AC and INCADDR are to be set next clock, then this is what A14 will be.
            int a14inc = ( ( ( i & mask ) + 1 ) & 64 ) != 0 ? 1 << 7 : 0;

            // If AC and REPLINE2 are to be set next clock, then this is what A14 will be.
            int a14rep = ( ( ( i & mask ) - 1 ) & 64 ) != 0 ? 1 << 8 : 0;

            int a14flags = a14inc | a14rep;

            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as8 ) & mask | a14flags;
            }
            if ( isSet( i, repline2 ) && isSet( i, ac ) )
            {
                // was REPLINE -- had to borrow 256

                // For A8..A13, just use the subtraction.
                int result = ( ( ( i & mask ) - 1 ) & 63 ) | a14flags;

                // For A14, use A14REP.
                result |= isSet( i, 8 ) ? 1 << 6 : 0;
                return result;
            }
            if ( isSet( i, incaddr ) && isSet( i, ac ) )
            {
                // INCADDR -- had to carry 256

                // For A8..A13, just use the addition.
                int result = ( ( ( i & mask ) + 1 ) & 63 ) | a14flags;

                // For A14, use A14INC.
                result |= isSet( i, 7 ) ? 1 << 6 : 0;
                return result;
            }
            // Don't change.
            return i & mask | a14flags;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "addr1",
                         inputs,
                         outputs,
                         new BitFormatter( "[x0-6] [7,FUDGE1] [8,FUDGE2]" ) );
    }

    private static Chip pixelconv()
    {
        List< String > outputNames = ImmutableList.of( "R0", "R1", "G0", "G1", "B0", "B1", "E0", "E1", "E2", "E3" );
        List< String > inputs = concat( bitRange( "D", 8 ), ImmutableList.of( "H0", "VIS" ) );
        int e0 = 6;
        int d0 = outputNames.size();
        int d4 = d0 + 4;
        int h0 = d4 + 4;
        int vis = h0 + 1;

        int[] palette =
        {
            // darker colours
            0b00_00_00,  // black
            0b00_00_10,  // red
            0b00_10_00,  // green
            0b00_10_10,  // yellow
            0b10_00_00,  // blue
            0b10_00_10,  // magenta
            0b10_10_00,  // cyan
            0b10_10_10,  // grey

            // lighter colours
            // placement of greys is deliberate to make the expressions shorter...
            0b01_01_01,  // black
            0b00_00_11,  // red
            0b00_11_00,  // green
            0b00_11_11,  // yellow
            0b11_00_00,  // blue
            0b11_00_10,  // magenta
            0b11_11_00,  // cyan
            0b11_11_11,  // white
        };

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            //
            // If H0->0 (i.e. next pixel is even), then:
            // - Save the bottom nibble for later use (since data may become invalid later).
            // - Compute RGB from lower nibble.
            //
            // If H0->1 (i.e. next pixel is odd), then:
            // - Stored nibble can be set to anything - ergo, save top nibble to reduce complexity.
            // - Compute RGB from stored nibble.
            //
            // Only output if VIS=1.

            int nibble0 = ( i >>> d0 ) & 15;
            int nibble1 = ( i >>> d4 ) & 15;
            int nibbleS = ( i >>> e0 ) & 15;

            int rgb = isSet( i, vis ) ? isSet( i, h0 ) ? palette[ nibble0 ] : palette[ nibbleS ] : 0;

            return rgb | ( nibble1 << e0 );
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "pixelconv",
                         inputs,
                         outputs,
                         new BitFormatter( "[0-1] [2-3] [4-5]" ) );
    }
}
