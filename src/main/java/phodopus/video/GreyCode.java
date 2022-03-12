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

public final class GreyCode
{
    private static final int MAX = 16;

    private static final int[] CODE;

    static
    {
        CODE = new int[ 1 << MAX ];

        // Make [0, 1] for bit 0.
        CODE[ 1 ] = 1;

        for ( int bit = 1; bit < MAX; bit++ )
        {
            // Reflect entries [0..permutations) and OR in the new bit.
            int permutations = 1 << bit;
            for ( int i = 0; i < permutations; i++ )
            {
                CODE[ permutations * 2 - i - 1 ] = permutations | CODE[ i ];
            }
        }
    }

    public static int code( int input )
    {
        return CODE[ input ];
    }
}
