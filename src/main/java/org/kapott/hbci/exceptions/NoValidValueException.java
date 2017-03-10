
/*  $Id: NoValidValueException.java,v 1.1 2011/05/04 22:38:01 willuhn Exp $

    This file is part of HBCI4Java
    Copyright (C) 2001-2008  Stefan Palme

    HBCI4Java is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    HBCI4Java is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package org.kapott.hbci.exceptions;

import org.kapott.hbci.manager.HBCIUtilsInternal;

public final class NoValidValueException
    extends HBCI_Exception
{
    public NoValidValueException(String path,String value)
    {
        super(HBCIUtilsInternal.getLocMsg("EXCMSG_INVVALUE",new Object[] {path,value}));
    }
}