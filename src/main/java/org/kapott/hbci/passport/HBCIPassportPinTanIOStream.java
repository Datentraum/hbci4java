/**********************************************************************
 *
 * This file is part of HBCI4Java.
 * Copyright (c) Olaf Willuhn
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 **********************************************************************/

package org.kapott.hbci.passport;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.kapott.hbci.dialog.DialogContext;
import org.kapott.hbci.dialog.DialogEvent;
import org.kapott.hbci.exceptions.HBCI_Exception;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.manager.HBCIUtilsInternal;
import org.kapott.hbci.passport.storage.PassportData;
import org.kapott.hbci.passport.storage.PassportStorage;
import org.kapott.hbci.status.HBCIRetVal;

/**
 * Implementierung eines PIN/TAN-Passport, welcher keine Daten im Dateisystem ablegt
 * sondern alle Daten im Speicher haelt.
 */
public class HBCIPassportPinTanIOStream extends HBCIPassportPinTan
{

  private HBCIPassportIOStreamCallback ioStreamCallBack;	

  
  
  /**
   * ct.
   * @param init Generische Init-Daten.
   */
  public HBCIPassportPinTanIOStream(Object init)
  {
     super(init);
  }
 
  /**
   * Erzeugt die Passport-Datei wenn noetig.
   * In eine extra Funktion ausgelagert, damit es von abgeleiteten Klassen ueberschrieben werden kann.
   */
  protected void create()
  {

	  if (ioStreamCallBack == null || ioStreamCallBack.getInputStream() == null) {
		  throw new NullPointerException("client.passport.PinTan.ioStreamCallback must not be null");
	  }
	  
      HBCIUtils.log("have to create new passport file",HBCIUtils.LOG_WARN);
      askForMissingData(true,true,true,true,true,true,true);
      saveChanges();
  }
  
  
  /**
   * @see org.kapott.hbci.passport.HBCIPassportPinTan#read()
   */
  @Override
  protected void read()
  {

	  // TODO A.Hachmann - Only create if the stream is empty?
	  ioStreamCallBack = (HBCIPassportIOStreamCallback)getClientData("init");

	  try {
		  if (ioStreamCallBack.getInputStream().available() == 0) {
			  create();
		  }
      } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	  }
	  
      PassportData data = PassportStorage.load(this,ioStreamCallBack.getInputStream());
      this.setCountry(data.country);
      this.setBLZ(data.blz);
      this.setHost(data.host);
      this.setPort(data.port);
      this.setUserId(data.userId);
      this.setSysId(data.sysId);
      this.setBPD(data.bpd);
      this.setUPD(data.upd);
      this.setHBCIVersion(data.hbciVersion);
      this.setCustomerId(data.customerId);
      this.setFilterType(data.filter);
      this.setAllowedTwostepMechanisms(data.twostepMechs);
      this.setCurrentTANMethod(data.tanMethod);
  }

  /**
   * @see org.kapott.hbci.passport.HBCIPassportPinTan#saveChanges()
   */
  @Override
  public void saveChanges()
  {
	  try {

          final PassportData data = new PassportData();
          
          data.country     = this.getCountry();
          data.blz         = this.getBLZ();
          data.host        = this.getHost();
          data.port        = this.getPort();
          data.userId      = this.getUserId();
          data.sysId       = this.getSysId();
          data.bpd         = this.getBPD();
          data.upd         = this.getUPD();

          data.hbciVersion = this.getHBCIVersion();
          data.customerId  = this.getCustomerId();
          data.filter      = this.getFilterType();
          
          final List<String> l = getAllowedTwostepMechanisms();
          HBCIUtils.log("saving two step mechs: " + l, HBCIUtils.LOG_DEBUG);
          data.twostepMechs = l;
          
          try
          {
              final String s = this.getCurrentTANMethod(false);
              HBCIUtils.log("saving current tan method: "+s, HBCIUtils.LOG_DEBUG);
              data.tanMethod = s;
          }
          catch (Exception e)
          {
              // Nur zur Sicherheit. In der obigen Funktion werden u.U. eine Menge Sachen losgetreten.
              // Wenn da irgendwas schief laeuft, soll deswegen nicht gleich das Speichern der Config
              // scheitern. Im Zweifel speichern wir dann halt das ausgewaehlte Verfahren erstmal nicht
              // und der User muss es beim naechsten Mal neu waehlen
              HBCIUtils.log("could not determine current tan methode, skipping: " + e.getMessage(),HBCIUtils.LOG_DEBUG);
              HBCIUtils.log(e,HBCIUtils.LOG_DEBUG2);
          }

          PassportStorage.save(this,data,ioStreamCallBack.getOutputStream());
      }
      catch (HBCI_Exception he)
      {
          throw he;
      }
      catch (Exception e)
      {
          throw new HBCI_Exception(HBCIUtilsInternal.getLocMsg("EXCMSG_PASSPORT_WRITEERR"),e);
      }  }
  
  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString()
  {
    return this.getFileName();
  }
  
  @Override
	public void onDialogEvent(DialogEvent event, DialogContext ctx) {
		
		super.onDialogEvent(event, ctx);
		
//		for(HBCIRetVal retVal: ctx.getMsgStatus().segStatus.getRetVals()) {
//        	if ("3955".equals(retVal.code)) {
//        		HBCIUtils.log("PushTan identified", HBCIUtils.LOG_INFO);
//        	}
//        }
		
	}

  
}


