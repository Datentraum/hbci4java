/**********************************************************************
 *
 * This file is part of HBCI4Java.
 * Copyright (c) 2018 Olaf Willuhn
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

package org.kapott.hbci.GV.parsers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXB;

import org.kapott.hbci.GV.SepaUtil;
import org.kapott.hbci.GV_Result.GVRKUms.BTag;
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.AccountIdentification4Choice;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.AccountReport18;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.ActiveOrHistoricCurrencyAndAmount;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.BalanceType12Code;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.BankToCustomerAccountReportV05;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.BankTransactionCodeStructure4;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.BranchAndFinancialInstitutionIdentification5;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.CashAccount24;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.CashAccount25;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.CashBalance3;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.CreditDebitCode;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.DateAndDateTimeChoice;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.Document;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.EntryDetails6;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.EntryTransaction7;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.FinancialInstitutionIdentification8;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.PartyIdentification43;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.Purpose2Choice;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.ReportEntry7;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.TransactionAgents3;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.TransactionParties3;
import org.kapott.hbci.sepa.jaxb.camt_052_001_05.TransactionReferences3;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Saldo;
import org.kapott.hbci.structures.Value;

/**
 * Parser zum Lesen von Umsaetzen im CAMT.052 Format in Version 001.05.
 */
public class ParseCamt05200105 extends AbstractCamtParser
{
    /**
     * @see org.kapott.hbci.GV.parsers.ISEPAParser#parse(java.io.InputStream, java.lang.Object)
     */
    @Override
    public void parse(InputStream xml, List<BTag> tage)
    {
        
        Document doc = JAXB.unmarshal(xml, Document.class);
        BankToCustomerAccountReportV05 container = doc.getBkToCstmrAcctRpt();

        // Dokument leer
        if (container == null)
        {
            HBCIUtils.log("camt document empty",HBCIUtils.LOG_WARN);
            return;
        }

        // Enthaelt per Definition genau einen Report von einem Buchungstag
        List<AccountReport18> reports = container.getRpt();
        if (reports == null || reports.size() == 0)
        {
            HBCIUtils.log("camt document empty",HBCIUtils.LOG_WARN);
            return;
        }
        
        // Per Definition enthaelt die Datei beim CAMT-Abruf zwar genau einen Buchungstag.
        // Da wir aber eine passende Datenstruktur haben, lesen wir mehr ein, falls
        // mehr vorhanden sind. Dann koennen wird den Parser spaeter auch nutzen,
        // um CAMT-Dateien aus anderen Quellen zu lesen.
        for (AccountReport18 report:reports)
        {
            ////////////////////////////////////////////////////////////////////
            // Kopf des Buchungstages
            BTag tag = this.createDay(report);
            tage.add(tag);
            //
            ////////////////////////////////////////////////////////////////////

            ////////////////////////////////////////////////////////////////////
            // Die einzelnen Buchungen
            BigDecimal saldo = tag.start != null && tag.start.value != null ? tag.start.value.getBigDecimalValue() : BigDecimal.ZERO;
            
            for (ReportEntry7 entry:report.getNtry())
            {
                UmsLine line = this.createLine(entry,saldo);
                if (line != null)
                {
                    tag.lines.add(line);
                    
                    // Saldo fortschreiben
                    saldo = line.saldo.value.getBigDecimalValue();
                }
            }
            //
            ////////////////////////////////////////////////////////////////////
        }
    }

    /**
     * Erzeugt eine einzelne Umsatzbuchung.
     * @param entry der Entry aus der CAMT-Datei.
     * @param der aktuelle Saldo vor dieser Buchung.
     * @return die Umsatzbuchung.
     */
    private UmsLine createLine(ReportEntry7 entry, BigDecimal currSaldo)
    {
        UmsLine line = new UmsLine();
        line.isSepa = true;
        line.isCamt = true;
        line.other = new Konto();
        
        List<EntryDetails6> details = entry.getNtryDtls();
        if (details.size() == 0)
            return null;
        
        // Das Schema sieht zwar mehrere Detail-Elemente vor, ich wuesste
        // aber ohnehin nicht, wie man das sinnvoll mappen koennte 
        EntryDetails6 detail = details.get(0);
        
        List<EntryTransaction7> txList = detail.getTxDtls();
        if (txList.size() == 0)
            return null;
        
        // Checken, ob es Soll- oder Habenbuchung ist
        boolean haben = entry.getCdtDbtInd() != null && entry.getCdtDbtInd() == CreditDebitCode.CRDT;
        
        // ditto
        EntryTransaction7 tx = txList.get(0);
        
        ////////////////////////////////////////////////////////////////////////
        // Buchungs-ID
        TransactionReferences3 ref = tx.getRefs();
        if (ref != null)
        {
            line.id = trim(ref.getPrtry() != null && ref.getPrtry().size() > 0 ? ref.getPrtry().get(0).getRef() : null);
            line.endToEndId = trim(ref.getEndToEndId());
            line.mandateId = trim(ref.getMndtId());
        }
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Gegenkonto: IBAN + Name
        TransactionParties3 other = tx.getRltdPties();
        if (other != null)
        {
            CashAccount24 acc = haben ? other.getDbtrAcct() : other.getCdtrAcct();
            AccountIdentification4Choice id = acc != null ? acc.getId() : null;
            line.other.iban = trim(id != null ? id.getIBAN() : null);
            
            PartyIdentification43 name = haben ? other.getDbtr() : other.getCdtr();
            line.other.name = trim(name != null ? name.getNm() : null);
            
            // Abweichender Name, falls vorhanden
            name = haben ? other.getUltmtDbtr() : other.getUltmtCdtr();
            line.other.name2 = trim(name != null ? name.getNm() : null);
        }
        //
        ////////////////////////////////////////////////////////////////////////
            
        ////////////////////////////////////////////////////////////////////////
        // Gegenkonto: BIC
        TransactionAgents3 banks = tx.getRltdAgts();
        if (banks != null)
        {
            BranchAndFinancialInstitutionIdentification5 bank = haben ? banks.getDbtrAgt() : banks.getCdtrAgt();
            FinancialInstitutionIdentification8 bic = bank != null ? bank.getFinInstnId() : null;
            line.other.bic = trim(bic != null ? bic.getBICFI() : null);
        }
        //
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Verwendungszweck
        List<String> usages = tx.getRmtInf() != null ? tx.getRmtInf().getUstrd() : null;
        if (usages != null && usages.size() > 0)
            line.usage.addAll(trim(usages));
        //
        ////////////////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////////////////
        // Betrag
        ActiveOrHistoricCurrencyAndAmount amt = entry.getAmt();
        BigDecimal bd = amt.getValue() != null ? amt.getValue() : BigDecimal.ZERO;
        line.value = new Value(this.checkDebit(bd,entry.getCdtDbtInd()));
        line.value.setCurr(amt.getCcy());
        //
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Storno-Kennzeichen
        // Laut Spezifikation kehrt sich bei Stornobuchungen im Gegensatz zu MT940
        // nicht das Vorzeichen um. Der Betrag bleibt also gleich
        line.isStorno = entry.isRvslInd() != null ? entry.isRvslInd().booleanValue() : false;
        //
        ////////////////////////////////////////////////////////////////////////


        ////////////////////////////////////////////////////////////////////////
        // Buchungs- und Valuta-Datum
        DateAndDateTimeChoice bdate = entry.getBookgDt();
        line.bdate = bdate != null ? SepaUtil.toDate(bdate.getDt()) : null;
        
        DateAndDateTimeChoice vdate = entry.getValDt();
        line.valuta = vdate != null ? SepaUtil.toDate(vdate.getDt()) : null;
        
        // Wenn einer von beiden Werten fehlt, uebernehmen wir dort den jeweils anderen
        if (line.bdate == null) line.bdate = line.valuta;
        if (line.valuta == null) line.valuta = line.bdate;
        //
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Saldo
        line.saldo = new Saldo();
        line.saldo.value = new Value(currSaldo.add(line.value.getBigDecimalValue()));
        line.saldo.value.setCurr(line.value.getCurr());
        line.saldo.timestamp = line.bdate;
        //
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Art und Kundenreferenz
        line.text = trim(entry.getAddtlNtryInf());
        line.customerref = trim(entry.getAcctSvcrRef());
        //
        ////////////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////////////
        // Primanota, GV-Code und GV-Code-Ergaenzung
        // Ich weiss nicht, ob das bei allen Banken so codiert ist.
        // Bei der Sparkasse ist es jedenfalls so.
        BankTransactionCodeStructure4 b = tx.getBkTxCd();
        String code = (b != null && b.getPrtry() != null) ? b.getPrtry().getCd() : null;
        if (code != null && code.contains("+"))
        {
            String[] parts = code.split("\\+");
            if (parts.length == 4)
            {
                line.gvcode    = parts[1];
                line.primanota = parts[2];
                line.addkey    = parts[3];
            }
        }
        //
        ////////////////////////////////////////////////////////////////////////

        ////////////////////////////////////////////////////////////////////////
        // Purpose-Code
        Purpose2Choice purp = tx.getPurp();
        line.purposecode = trim(purp != null ? purp.getCd() : null);
        //
        ////////////////////////////////////////////////////////////////////////
        
        return line;
    }
    
    
    /**
     * Erzeugt einen neuen Buchungstag.
     * @param report der Report.
     * @return der erzeugte Buchungstag.
     */
    private BTag createDay(AccountReport18 report)
    {
        BTag tag = new BTag();
        tag.start = new Saldo();
        tag.end = new Saldo();
        tag.starttype = 'F';
        tag.endtype = 'F';

        ////////////////////////////////////////////////////////////////
        // Start- un End-Saldo ermitteln
        final long day = 24 * 60 * 60 * 1000L; 
        for (CashBalance3 bal:report.getBal())
        {
            BalanceType12Code code = bal.getTp().getCdOrPrtry().getCd();
            
            // Schluss-Saldo vom Vortag
            if (code == BalanceType12Code.PRCD)
            {
                tag.start.value = new Value(this.checkDebit(bal.getAmt().getValue(),bal.getCdtDbtInd()));
                tag.start.value.setCurr(bal.getAmt().getCcy());
                
                //  Wir erhoehen noch das Datum um einen Tag, damit aus dem
                // Schlusssaldo des Vortages der Startsaldo des aktuellen Tages wird.
                tag.start.timestamp = new Date(SepaUtil.toDate(bal.getDt().getDt()).getTime() + day);
            }
            
            // End-Saldo
            else if (code == BalanceType12Code.CLBD)
            {
                tag.end.value = new Value(this.checkDebit(bal.getAmt().getValue(),bal.getCdtDbtInd()));
                tag.end.value.setCurr(bal.getAmt().getCcy());
                tag.end.timestamp = SepaUtil.toDate(bal.getDt().getDt());
            }
        }
        //
        ////////////////////////////////////////////////////////////////
        
        ////////////////////////////////////////////////////////////////
        // Das eigene Konto ermitteln
        CashAccount25 acc = report.getAcct();
        tag.my = new Konto();
        tag.my.iban = trim(acc.getId().getIBAN());
        tag.my.curr = trim(acc.getCcy());
        
        BranchAndFinancialInstitutionIdentification5 bank = acc.getSvcr();
        if (bank != null && bank.getFinInstnId() != null)
        tag.my.bic  = trim(bank.getFinInstnId().getBICFI());
        ////////////////////////////////////////////////////////////////
        
        return tag;
    }
    
    /**
     * Prueft, ob es sich um einen Soll-Betrag handelt und setzt in dem Fall ein negatives Vorzeichen vor den Wert.
     * @param d die zu pruefende Zahl.
     * @param code das Soll-/Haben-Kennzeichen.
     * @return der ggf korrigierte Betrag.
     */
    private BigDecimal checkDebit(BigDecimal d, CreditDebitCode code)
    {
        if (d == null || code == null || code == CreditDebitCode.CRDT)
            return d;
        
        return BigDecimal.ZERO.subtract(d);
    }
}


