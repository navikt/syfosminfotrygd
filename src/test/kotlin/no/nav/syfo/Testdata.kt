package no.nav.syfo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.syfo.diagnose.Kodeverk
import no.nav.syfo.model.sykmelding.Adresse
import no.nav.syfo.model.sykmelding.AktivitetIkkeMulig
import no.nav.syfo.model.sykmelding.AnnenFraversArsak
import no.nav.syfo.model.sykmelding.Arbeidsgiver
import no.nav.syfo.model.sykmelding.ArbeidsrelatertArsak
import no.nav.syfo.model.sykmelding.AvsenderSystem
import no.nav.syfo.model.sykmelding.Behandler
import no.nav.syfo.model.sykmelding.Diagnose
import no.nav.syfo.model.sykmelding.ErIArbeid
import no.nav.syfo.model.sykmelding.ErIkkeIArbeid
import no.nav.syfo.model.sykmelding.Gradert
import no.nav.syfo.model.sykmelding.HarArbeidsgiver
import no.nav.syfo.model.sykmelding.KontaktMedPasient
import no.nav.syfo.model.sykmelding.MedisinskArsak
import no.nav.syfo.model.sykmelding.MedisinskArsakType
import no.nav.syfo.model.sykmelding.MedisinskVurdering
import no.nav.syfo.model.sykmelding.MeldingTilNAV
import no.nav.syfo.model.sykmelding.Merknad
import no.nav.syfo.model.sykmelding.Periode
import no.nav.syfo.model.sykmelding.Prognose
import no.nav.syfo.model.sykmelding.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.SporsmalSvar
import no.nav.syfo.model.sykmelding.Sykmelding
import no.nav.syfo.model.sykmelding.UtenlandskSykmelding
import no.nav.syfo.smregister.BehandlingsutfallDTO
import no.nav.syfo.smregister.DiagnoseDTO
import no.nav.syfo.smregister.MedisinskVurderingDTO
import no.nav.syfo.smregister.RegelStatusDTO
import no.nav.syfo.smregister.SykmeldingDTO
import no.nav.syfo.smregister.SykmeldingsperiodeDTO

fun createDefaultHealthInformation(): HelseOpplysningerArbeidsuforhet =
    HelseOpplysningerArbeidsuforhet().apply {
        regelSettVersjon = "1"
        aktivitet =
            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(4)
                        aktivitetIkkeMulig =
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()
                                .apply { medisinskeArsaker = ArsakType().apply {} }
                    },
                )
            }
        pasient =
            HelseOpplysningerArbeidsuforhet.Pasient().apply {
                fodselsnummer =
                    Ident().apply {
                        id = "12343567"
                        typeId =
                            CV().apply {
                                dn = "Fødselsnummer"
                                s = "2.16.578.1.12.4.1.1.8116"
                                v = "FNR"
                            }
                    }
            }
        syketilfelleStartDato = LocalDate.now()
        medisinskVurdering =
            HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                hovedDiagnose =
                    HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                        diagnosekode =
                            CV().apply {
                                dn = "Problem med jus/politi"
                                s = "2.16.578.1.12.4.1.1.7110"
                                v = "Z09"
                            }
                    }
            }
    }

fun receivedSykmelding(
    id: String,
    sykmelding: Sykmelding = generateSykmelding(),
    fellesformat: String = "",
    merknader: List<Merknad>? = null,
    tssid: String? = "2344123",
    utenlandskSykmelding: UtenlandskSykmelding? = null,
) =
    ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = "123124",
        tlfPasient = "12314",
        personNrLege = "123145",
        navLogId = "0412",
        msgId = id,
        legekontorOrgNr = "",
        legekontorHerId = "",
        legekontorReshId = "",
        legekontorOrgName = "Legevakt",
        mottattDato = LocalDateTime.now(),
        rulesetVersion = "",
        fellesformat = fellesformat,
        tssid = tssid,
        merknader = merknader,
        partnerreferanse = null,
        legeHprNr = null,
        legeHelsepersonellkategori = null,
        vedlegg = null,
        utenlandskSykmelding = utenlandskSykmelding,
    )

fun generateSykmeldingDto(
    perioder: List<SykmeldingsperiodeDTO> = emptyList(),
    merknader: List<no.nav.syfo.smregister.Merknad>? = emptyList(),
): SykmeldingDTO {
    return SykmeldingDTO(
        id = UUID.randomUUID().toString(),
        merknader = merknader,
        behandletTidspunkt = OffsetDateTime.now(),
        medisinskVurdering = MedisinskVurderingDTO(hovedDiagnose = DiagnoseDTO("kode")),
        behandlingsutfall = BehandlingsutfallDTO(status = RegelStatusDTO.OK),
        sykmeldingsperioder = perioder,
    )
}

fun generateSykmelding(
    id: String = UUID.randomUUID().toString(),
    pasientAktoerId: String = UUID.randomUUID().toString(),
    medisinskVurdering: MedisinskVurdering = generateMedisinskVurdering(),
    skjermetForPasient: Boolean = false,
    perioder: List<Periode> = listOf(generatePeriode()),
    prognose: Prognose = generatePrognose(),
    utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>> = mapOf(),
    tiltakArbeidsplassen: String? = null,
    tiltakNAV: String? = null,
    andreTiltak: String? = null,
    meldingTilNAV: MeldingTilNAV? = null,
    meldingTilArbeidsgiver: String? = null,
    kontaktMedPasient: KontaktMedPasient = generateKontaktMedPasient(),
    behandletTidspunkt: LocalDateTime = LocalDateTime.now(),
    behandler: Behandler = generateBehandler(),
    avsenderSystem: AvsenderSystem = generateAvsenderSystem(),
    arbeidsgiver: Arbeidsgiver = generateArbeidsgiver(),
    msgid: String = UUID.randomUUID().toString(),
    syketilfelleStartDato: LocalDate? = null,
    signaturDato: LocalDateTime = LocalDateTime.now(),
    navnFastlege: String = UUID.randomUUID().toString(),
) =
    Sykmelding(
        id = id,
        msgId = msgid,
        pasientAktoerId = pasientAktoerId,
        medisinskVurdering = medisinskVurdering,
        skjermesForPasient = skjermetForPasient,
        perioder = perioder,
        prognose = prognose,
        utdypendeOpplysninger = utdypendeOpplysninger,
        tiltakArbeidsplassen = tiltakArbeidsplassen,
        tiltakNAV = tiltakNAV,
        andreTiltak = andreTiltak,
        meldingTilNAV = meldingTilNAV,
        meldingTilArbeidsgiver = meldingTilArbeidsgiver,
        kontaktMedPasient = kontaktMedPasient,
        behandletTidspunkt = behandletTidspunkt,
        behandler = behandler,
        avsenderSystem = avsenderSystem,
        arbeidsgiver = arbeidsgiver,
        syketilfelleStartDato = syketilfelleStartDato,
        signaturDato = signaturDato,
        navnFastlege = navnFastlege,
    )

fun generateMedisinskVurdering(
    hovedDiagnose: Diagnose? = generateDiagnose(),
    bidiagnoser: List<Diagnose> = listOf(),
    svangerskap: Boolean = false,
    yrkesskade: Boolean = false,
    yrkesskadeDato: LocalDate? = null,
    annenFraversArsak: AnnenFraversArsak? = null,
) =
    MedisinskVurdering(
        hovedDiagnose = hovedDiagnose,
        biDiagnoser = bidiagnoser,
        svangerskap = svangerskap,
        yrkesskade = yrkesskade,
        yrkesskadeDato = yrkesskadeDato,
        annenFraversArsak = annenFraversArsak,
    )

fun generateDiagnose() = ICPC2.values()[Random.nextInt(ICPC2.values().size)].toDiagnose()

fun Kodeverk.toDiagnose() = Diagnose(system = oid, kode = codeValue, tekst = text)

fun generatePeriode(
    fom: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now().plusDays(10),
    aktivitetIkkeMulig: AktivitetIkkeMulig? = generateAktivitetIkkeMulig(),
    avventendeInnspillTilArbeidsgiver: String? = null,
    behandlingsdager: Int? = null,
    gradert: Gradert? = null,
    reisetilskudd: Boolean = false,
) =
    Periode(
        fom = fom,
        tom = tom,
        aktivitetIkkeMulig = aktivitetIkkeMulig,
        avventendeInnspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        behandlingsdager = behandlingsdager,
        gradert = gradert,
        reisetilskudd = reisetilskudd,
    )

fun generateAktivitetIkkeMulig(
    medisinskArsak: MedisinskArsak? = generateMedisinskArsak(),
    arbeidsrelatertArsak: ArbeidsrelatertArsak? = null,
) =
    AktivitetIkkeMulig(
        medisinskArsak = medisinskArsak,
        arbeidsrelatertArsak = arbeidsrelatertArsak,
    )

fun generateMedisinskArsak(
    beskrivelse: String = "test data",
    arsak: List<MedisinskArsakType> =
        listOf(MedisinskArsakType.values()[Random.nextInt(MedisinskArsakType.values().size)]),
) =
    MedisinskArsak(
        beskrivelse = beskrivelse,
        arsak = arsak,
    )

fun generatePrognose(
    arbeidsforEtterPeriode: Boolean = true,
    hennsynArbeidsplassen: String? = null,
    erIArbeid: ErIArbeid? = generateErIArbeid(),
    erIkkeIArbeid: ErIkkeIArbeid? = null,
) =
    Prognose(
        arbeidsforEtterPeriode = arbeidsforEtterPeriode,
        hensynArbeidsplassen = hennsynArbeidsplassen,
        erIArbeid = erIArbeid,
        erIkkeIArbeid = erIkkeIArbeid,
    )

fun generateErIArbeid(
    egetArbeidPaSikt: Boolean = true,
    annetArbeidPaSikt: Boolean = true,
    arbeidFOM: LocalDate = LocalDate.now().plusDays(30),
    vurderingsdato: LocalDate = LocalDate.now(),
) =
    ErIArbeid(
        egetArbeidPaSikt = egetArbeidPaSikt,
        annetArbeidPaSikt = annetArbeidPaSikt,
        arbeidFOM = arbeidFOM,
        vurderingsdato = vurderingsdato,
    )

fun generateKontaktMedPasient(
    kontaktDato: LocalDate? = LocalDate.now(),
    begrunnelseIkkeKontakt: String? = null,
) = KontaktMedPasient(kontaktDato = kontaktDato, begrunnelseIkkeKontakt = begrunnelseIkkeKontakt)

fun generateBehandler(
    fornavn: String = "Fornavn",
    mellomnavn: String? = "Mellomnavn",
    etternavn: String = "Etternavnsen",
    aktoerId: String = "128731827",
    fnr: String = "1234567891",
    hpr: String? = null,
    her: String? = null,
    adresse: Adresse = generateAdresse(),
    tlf: String? = null,
) =
    Behandler(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        aktoerId = aktoerId,
        fnr = fnr,
        hpr = hpr,
        her = her,
        adresse = adresse,
        tlf = tlf,
    )

fun generateAdresse(
    gate: String? = "Gate",
    postnummer: Int? = 557,
    kommune: String? = "Oslo",
    postboks: String? = null,
    land: String? = "NO",
) =
    Adresse(
        gate = gate,
        postnummer = postnummer,
        kommune = kommune,
        postboks = postboks,
        land = land,
    )

fun generateAvsenderSystem(
    navn: String = "test",
    versjon: String = "1.2.3",
) =
    AvsenderSystem(
        navn = navn,
        versjon = versjon,
    )

fun generateArbeidsgiver(
    harArbeidsgiver: HarArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
    legekontor: String = "HelseHus",
    yrkesbetegnelse: String = "Maler",
    stillingsprosent: Int = 100,
) =
    Arbeidsgiver(
        harArbeidsgiver = harArbeidsgiver,
        navn = legekontor,
        yrkesbetegnelse = yrkesbetegnelse,
        stillingsprosent = stillingsprosent,
    )
