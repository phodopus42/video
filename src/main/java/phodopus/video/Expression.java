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

import com.google.common.collect.ImmutableList;

public final class Expression
{
    private final String name;

    private final List< And > ands;

    public Expression( String name, Iterable< And > ands )
    {
        this.name = name;
        this.ands = ImmutableList.copyOf( ands );
    }

    public String name()
    {
        return this.name;
    }

    public List< And > ands()
    {
        return this.ands;
    }

    public boolean evaluate( int input )
    {
        for ( And and : this.ands )
        {
            if ( and.test( input ) )
            {
                return true;
            }
        }
        return false;
    }
}
