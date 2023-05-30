package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.generateSykmeldingDto
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.smregister.PeriodetypeDTO
import no.nav.syfo.smregister.SmregisterClient
import no.nav.syfo.smregister.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class SykmeldingServiceTest : FunSpec({
    val smregisterClient = mockk<SmregisterClient>()
    val sykmeldingService = SykmeldingService(smregisterClient)
    context("test overlappendede perioder fra registeret") {

        test("ingen sykmeldinger fra registeret") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 8) // sunday

            coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns emptyList()
            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo false
        }

        test("should overlapp if period is same") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 8) // sunday

            coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, tom, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo true
        }

        test("Should overlapp if sykmelding does not include saturday and sunday") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 6) // friday

            coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, tom.plusDays(2), null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo true
        }
        test("should overlapp if new sykmelding inculdes saturday and sunday but not the old ones") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 8) // sunday

            coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, tom.minusDays(2), null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo true
        }

        test("should overlap when register contains two sykmeldinger without weekend") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 15) // sunday

            coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, fom.plusDays(4), null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom.plusDays(7), tom, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo true
        }

        test("should not overlap when register contains two sykmeldinger without weekend with one day gap") {
            val fom = LocalDate.of(2023, 1, 2) // monday
            val tom = LocalDate.of(2023, 1, 15) // sunday

            coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, fom.plusDays(4), null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom.plusDays(8), tom, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(fom, tom)),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo false
        }

        test("should overlap when register when sykmelding has sykmelding surrounding days") {
            val fom = LocalDate.of(2023, 1, 1) // monday
            val tom = LocalDate.of(2023, 1, 15) // sunday

            coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns listOf(
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom, fom.plusDays(4), null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
                generateSykmeldingDto(
                    perioder = listOf(
                        SykmeldingsperiodeDTO(fom.plusDays(8), tom, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG),
                    ),
                ),
            )

            val receivedSykmelding = receivedSykmelding(
                "1",
                sykmelding = generateSykmelding(
                    perioder = listOf(generatePeriode(LocalDate.of(2023, 1, 7), LocalDate.of(2023, 1, 9))),
                ),
            )

            val overlapper = sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
            overlapper shouldBeEqualTo true
        }
    }
})
