package no.nav.syfo.services.updateinfotrygd

import java.time.LocalDate
import kotlin.test.Test
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import org.amshove.kluent.shouldBeEqualTo

class FindOprasjonstypeNewTest {

    @Test
    fun `No sykmelding in infotrygd`() {
        val fom = LocalDate.of(2020, 1, 5)
        val tom = LocalDate.of(2020, 1, 10)

        val listSMinfo = emptyList<TypeSMinfo>()

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)

        result shouldBeEqualTo (Operasjonstype.NY to fom)
    }

    @Test
    fun `Sykmelding starts before infotrygd and is overlapping`() {
        val fom = LocalDate.of(2020, 1, 5)
        val tom = LocalDate.of(2020, 1, 10)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 10) to LocalDate.of(2020, 1, 20)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to fom)
    }

    @Test
    fun `Sykmelding starts before infotrygd should be extension should give UGYLIDGO overlapping`() {
        val fom = LocalDate.of(2020, 1, 5)
        val tom = LocalDate.of(2020, 1, 10)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 11) to LocalDate.of(2020, 1, 20)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to fom)
    }

    @Test
    fun `Sykmelding starts before infotrygd should not be extensin with 1 day (NY)`() {
        val fom = LocalDate.of(2020, 1, 5)
        val tom = LocalDate.of(2020, 1, 10)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 12) to LocalDate.of(2020, 1, 20)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.NY to fom)
    }

    @Test
    fun `Sykmelding in infotrygd and should be UGYLIDG_OVERLAPP`() {
        val fom = LocalDate.of(2020, 1, 1)
        val tom = LocalDate.of(2020, 1, 2)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 2) to LocalDate.of(2020, 1, 2)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to fom)
    }

    @Test
    fun `Sykmelding in infotrygd and should be UGYLIDG_OVERLAPP 2`() {
        val fom = LocalDate.of(2020, 1, 1)
        val tom = LocalDate.of(2020, 1, 2)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 3) to LocalDate.of(2020, 1, 4)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to fom)
    }

    @Test
    fun `Sykmelding in infotrygd and should be NY sykmelding with one day gap`() {
        val fom = LocalDate.of(2020, 1, 1)
        val tom = LocalDate.of(2020, 1, 2)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 4) to LocalDate.of(2020, 1, 5)))

        val result = findoperasjonstypeAndFom(fom, tom, listSMinfo)
        result shouldBeEqualTo (Operasjonstype.NY to fom)
    }

    @Test
    fun `Sykmelding in infotrygd should be UENDRET`() {
        val fom = LocalDate.of(2020, 1, 1)
        val tom = LocalDate.of(2020, 1, 10)

        val listSMinfo = getSmInfos(listOf(LocalDate.of(2020, 1, 1) to LocalDate.of(2020, 1, 10)))

        findoperasjonstypeAndFom(fom, tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.UENDRET to fom)
        findoperasjonstypeAndFom(fom.plusDays(1), tom.minusDays(1), listSMinfo) shouldBeEqualTo
            (Operasjonstype.UENDRET to LocalDate.of(2020, 1, 1))
    }

    @Test
    fun `Sykmelding in infotrygd should be ENDRING when fom is same but TOM is longer`() {
        val iFom = LocalDate.of(2020, 1, 1)
        val fom = iFom
        val tom = LocalDate.of(2020, 1, 20)

        val listSMinfo = getSmInfos(listOf(iFom to LocalDate.of(2020, 1, 10)))

        findoperasjonstypeAndFom(fom, tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.ENDRING to fom)
    }

    @Test
    fun `Sykmelding should be FORLENGELSE`() {
        val iFom = LocalDate.of(2020, 1, 1)
        val fom = iFom
        val tom = LocalDate.of(2020, 1, 20)

        val listSMinfo = getSmInfos(listOf(iFom to LocalDate.of(2020, 1, 10)))

        findoperasjonstypeAndFom(fom.plusDays(1), tom.plusDays(20), listSMinfo) shouldBeEqualTo
            (Operasjonstype.FORLENGELSE to iFom)
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 1, 10),
            tom.plusDays(20),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.FORLENGELSE to iFom)
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 1, 11),
            tom.plusDays(20),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.FORLENGELSE to iFom)
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 1, 12),
            tom.plusDays(20),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.NY to LocalDate.of(2020, 1, 12))
    }

    @Test
    fun `Ny Inbetween infotrygd sykmelding`() {
        val fom = LocalDate.of(2020, 1, 12)
        val tom = LocalDate.of(2020, 1, 18)

        val iFirstFom = LocalDate.of(2020, 1, 1)
        val iSecondFom = LocalDate.of(2020, 1, 20)
        val iThiredFom = LocalDate.of(2020, 2, 10)
        val listSMinfo =
            getSmInfos(
                listOf(
                    iFirstFom to LocalDate.of(2020, 1, 10),
                    iSecondFom to LocalDate.of(2020, 1, 30),
                    iThiredFom to LocalDate.of(2020, 2, 20)
                )
            )

        findoperasjonstypeAndFom(fom, tom, listSMinfo) shouldBeEqualTo (Operasjonstype.NY to fom)
        findoperasjonstypeAndFom(fom.plusDays(1), tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.NY to fom.plusDays(1))
        findoperasjonstypeAndFom(fom.minusDays(1), tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.FORLENGELSE to iFirstFom)
        findoperasjonstypeAndFom(fom.minusDays(2), tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.FORLENGELSE to iFirstFom)
        findoperasjonstypeAndFom(fom.minusDays(3), tom, listSMinfo) shouldBeEqualTo
            (Operasjonstype.FORLENGELSE to iFirstFom)
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 1, 1).minusDays(1),
            tom,
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to LocalDate.of(2020, 1, 1).minusDays(1))
        findoperasjonstypeAndFom(fom, tom.plusDays(1), listSMinfo) shouldBeEqualTo
            (Operasjonstype.UGYLDIG_OVERLAPP to fom)
        findoperasjonstypeAndFom(fom, tom.plusDays(20), listSMinfo) shouldBeEqualTo
            (Operasjonstype.UGYLDIG_OVERLAPP to fom)
        findoperasjonstypeAndFom(fom.minusDays(20), tom.plusDays(20), listSMinfo) shouldBeEqualTo
            (Operasjonstype.UGYLDIG_OVERLAPP to fom.minusDays(20))
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 2, 2),
            LocalDate.of(2020, 2, 8),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.NY to LocalDate.of(2020, 2, 2))
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 2, 2),
            LocalDate.of(2020, 2, 9),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.UGYLDIG_OVERLAPP to LocalDate.of(2020, 2, 2))
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 2, 21),
            LocalDate.of(2020, 2, 28),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.FORLENGELSE to iThiredFom)
        findoperasjonstypeAndFom(
            LocalDate.of(2020, 2, 22),
            LocalDate.of(2020, 2, 28),
            listSMinfo
        ) shouldBeEqualTo (Operasjonstype.NY to LocalDate.of(2020, 2, 22))
    }
}

private fun getSmInfos(perioder: List<Pair<LocalDate, LocalDate>>): List<TypeSMinfo> =
    perioder.map {
        TypeSMinfo().apply {
            periode =
                TypeSMinfo.Periode().apply {
                    arbufoerFOM = it.first
                    arbufoerTOM = it.second
                }
        }
    }
