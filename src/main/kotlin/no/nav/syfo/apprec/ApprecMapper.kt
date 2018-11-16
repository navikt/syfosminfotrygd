package no.nav.syfo.apprec

import no.kith.xmlstds.apprec._2004_11_21.XMLAdditionalId
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.apprec._2004_11_21.XMLCS
import no.kith.xmlstds.apprec._2004_11_21.XMLCV as AppRecCV
import no.kith.xmlstds.apprec._2004_11_21.XMLHCP
import no.kith.xmlstds.apprec._2004_11_21.XMLHCPerson
import no.kith.xmlstds.apprec._2004_11_21.XMLInst
import no.kith.xmlstds.apprec._2004_11_21.XMLOriginalMsgId
import no.kith.xmlstds.msghead._2006_05_24.XMLCV as MsgHeadCV
import no.kith.xmlstds.msghead._2006_05_24.XMLHealthcareProfessional
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.nav.helse.sm2013.EIFellesformat
import no.nav.syfo.get
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import java.time.LocalDateTime

fun createApprec(fellesformat: EIFellesformat, apprecStatus: ApprecStatus): XMLEIFellesformat {
    val fellesformatApprec = XMLEIFellesformat().apply {
        any.add(XMLMottakenhetBlokk().apply {
            ediLoggId = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
            ebRole = ApprecConstant.ebRoleNav.string
            ebService = ApprecConstant.ebService.string
            ebAction = ApprecConstant.ebActionSvarmelding.string
        }
        )
        any.add(XMLAppRec().apply {
            msgType = no.kith.xmlstds.apprec._2004_11_21.XMLCS().apply {
                v = ApprecConstant.apprec.string
            }
            miGversion = ApprecConstant.apprecVersionV1_0.string
            genDate = LocalDateTime.now()
            id = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId

            sender = XMLAppRec.Sender().apply {
                hcp = fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.intoHCP()
            }

            receiver = XMLAppRec.Receiver().apply {
                hcp = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.intoHCP()
            }

            status = XMLCS().apply {
                v = apprecStatus.v
                dn = apprecStatus.dn
            }

            originalMsgId = XMLOriginalMsgId().apply {
                msgType = XMLCS().apply {
                    v = fellesformat.get<XMLMsgHead>().msgInfo.type.v
                    dn = fellesformat.get<XMLMsgHead>().msgInfo.type.dn
                }
                issueDate = fellesformat.get<XMLMsgHead>().msgInfo.genDate
                id = fellesformat.get<XMLMsgHead>().msgInfo.msgId
            }
        }
        )
    }

    return fellesformatApprec
}

fun XMLHealthcareProfessional.intoHCPerson(): XMLHCPerson = XMLHCPerson().apply {
    name = if (middleName == null) "$familyName $givenName" else "$familyName $givenName $middleName"
    id = ident.first().id
    typeId = ident.first().typeId.intoCS()
    additionalId += ident.drop(1)
}

fun XMLOrganisation.intoHCP(): XMLHCP = XMLHCP().apply {
    inst = ident.first().intoInst().apply {
        name = organisationName
        additionalId += ident.drop(1)

        if (healthcareProfessional != null) {
            hcPerson += healthcareProfessional.intoHCPerson()
        }
    }
}

fun XMLIdent.intoInst(): XMLInst {
    val ident = this
    return XMLInst().apply {
        id = ident.id
        typeId = ident.typeId.intoCS()
    }
}

fun MsgHeadCV.intoCS(): XMLCS {
    val msgHeadCV = this
    return XMLCS().apply {
        dn = msgHeadCV.dn
        v = msgHeadCV.v
    }
}

operator fun MutableList<XMLAdditionalId>.plusAssign(idents: Iterable<XMLIdent>) {
    this.addAll(idents.map { it.intoAdditionalId() })
}

fun XMLIdent.intoAdditionalId(): XMLAdditionalId {
    val ident = this
    return XMLAdditionalId().apply {
        id = ident.id
        type = XMLCS().apply {
            dn = ident.typeId.dn
            v = ident.typeId.v
        }
    }
}

fun mapApprecErrorToAppRecCV(apprecError: ApprecError): AppRecCV = AppRecCV().apply {
    dn = apprecError.dn
    v = apprecError.v
    s = apprecError.s
}
