package no.nav.syfo.services

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import no.nav.helse.infotrygd.foresp.InfotrygdForesp
import no.nav.helse.infotrygd.foresp.TypeSMinfo
import no.nav.syfo.generatePeriode
import no.nav.syfo.generateSykmelding
import no.nav.syfo.generateSykmeldingDto
import no.nav.syfo.receivedSykmelding
import no.nav.syfo.smregister.PeriodetypeDTO
import no.nav.syfo.smregister.SmregisterClient
import no.nav.syfo.smregister.SykmeldingsperiodeDTO
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingServiceTest :
    FunSpec({
        val smregisterClient = mockk<SmregisterClient>()
        val sykmeldingService = SykmeldingService(smregisterClient)
        context("test overlappendede perioder") {
            context("Should overlap") {
                test("should overlapp if period is same") {
                    val fom = LocalDate.of(2023, 1, 2) // monday
                    val tom = LocalDate.of(2023, 1, 8) // sunday

                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom
                                                    arbufoerTOM = tom
                                                }
                                        },
                                    )
                                }
                        }
                    coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom,
                                            tom,
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )

                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }

                test("Should overlapp if sykmelding does not include saturday and sunday") {
                    val fom = LocalDate.of(2023, 1, 2) // monday
                    val tom = LocalDate.of(2023, 1, 6) // friday

                    coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom,
                                            tom.plusDays(2),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom
                                                    arbufoerTOM = tom.plusDays(2)
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }
                test("should overlapp if new sykmelding inculdes saturday and sunday") {
                    val fom = LocalDate.of(2023, 1, 2) // tuesday
                    val tom = LocalDate.of(2023, 1, 8) // sunday

                    coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom,
                                            tom.minusDays(2),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom
                                                    arbufoerTOM = tom.minusDays(2)
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }

                test("should overlap if new sykmelding starts on saturday, but not the old one") {
                    coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            LocalDate.of(2023, 1, 9),
                                            LocalDate.of(2023, 1, 13),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = LocalDate.of(2023, 1, 9)
                                                    arbufoerTOM = LocalDate.of(2023, 1, 13)
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder =
                                        listOf(
                                            generatePeriode(
                                                LocalDate.of(2023, 1, 7),
                                                LocalDate.of(2023, 1, 15)
                                            )
                                        ),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }

                test("should overlap when register contains two sykmeldinger without weekend") {
                    val fom = LocalDate.of(2023, 1, 2) // monday
                    val tom = LocalDate.of(2023, 1, 15) // sunday

                    coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom,
                                            fom.plusDays(4),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom.plusDays(7),
                                            tom,
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom
                                                    arbufoerTOM = tom.plusDays(2)
                                                }
                                        },
                                    )
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom.plusDays(7)
                                                    arbufoerTOM = tom
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }

                test(
                    "should overlap when register when sykmelding has sykmelding surrounding days"
                ) {
                    coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            LocalDate.of(2023, 1, 1),
                                            LocalDate.of(2023, 1, 6),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                                        ),
                                    ),
                            ),
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            LocalDate.of(2023, 1, 9),
                                            LocalDate.of(2023, 1, 13),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = LocalDate.of(2023, 1, 1)
                                                    arbufoerTOM = LocalDate.of(2023, 1, 6)
                                                }
                                        },
                                    )
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = LocalDate.of(2023, 1, 9)
                                                    arbufoerTOM = LocalDate.of(2023, 1, 13)
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder =
                                        listOf(
                                            generatePeriode(
                                                LocalDate.of(2023, 1, 7),
                                                LocalDate.of(2023, 1, 9)
                                            )
                                        ),
                                ),
                        )
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    overlapper shouldBeEqualTo true
                    overlapperInfotrygd shouldBeEqualTo true
                }
            }

            context("Should not overlap") {
                test("ingen tidligere registrerte perioder") {
                    val fom = LocalDate.of(2023, 1, 2) // monday
                    val tom = LocalDate.of(2023, 1, 8) // sunday

                    coEvery { smregisterClient.getSykmeldinger("123124", fom, tom) } returns
                        emptyList()
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )
                    val infotrygdForesp = InfotrygdForesp()
                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo false
                    overlapperInfotrygd shouldBeEqualTo false
                }

                test(
                    "should not overlap when register contains two sykmeldinger without weekend with one day gap"
                ) {
                    val fom = LocalDate.of(2023, 1, 2) // monday
                    val tom = LocalDate.of(2023, 1, 15) // sunday

                    coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom,
                                            fom.plusDays(4),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            fom.plusDays(8),
                                            tom,
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom
                                                    arbufoerTOM = fom.plusDays(4)
                                                }
                                        },
                                    )
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = fom.plusDays(8)
                                                    arbufoerTOM = tom
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder = listOf(generatePeriode(fom, tom)),
                                ),
                        )
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    overlapper shouldBeEqualTo false
                    overlapperInfotrygd shouldBeEqualTo false
                }

                test("fom is before fom in sykmelding in register") {
                    coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            LocalDate.of(2023, 1, 3),
                                            LocalDate.of(2023, 1, 7),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder =
                                        listOf(
                                            generatePeriode(
                                                LocalDate.of(2023, 1, 2),
                                                LocalDate.of(2023, 1, 7)
                                            )
                                        ),
                                ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = LocalDate.of(2023, 1, 3)
                                                    arbufoerTOM = LocalDate.of(2023, 1, 7)
                                                }
                                        },
                                    )
                                }
                        }
                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo false
                    overlapperInfotrygd shouldBeEqualTo false
                }

                test("tom is after tom in sykmelding in register") {
                    coEvery { smregisterClient.getSykmeldinger(any(), any(), any()) } returns
                        listOf(
                            generateSykmeldingDto(
                                perioder =
                                    listOf(
                                        SykmeldingsperiodeDTO(
                                            LocalDate.of(2023, 1, 3),
                                            LocalDate.of(2023, 1, 11),
                                            null,
                                            PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                                        ),
                                    ),
                            ),
                        )
                    val infotrygdForesp =
                        InfotrygdForesp().apply {
                            sMhistorikk =
                                InfotrygdForesp.SMhistorikk().apply {
                                    sykmelding.add(
                                        TypeSMinfo().apply {
                                            periode =
                                                TypeSMinfo.Periode().apply {
                                                    arbufoerFOM = LocalDate.of(2023, 1, 3)
                                                    arbufoerTOM = LocalDate.of(2023, 1, 11)
                                                }
                                        },
                                    )
                                }
                        }
                    val receivedSykmelding =
                        receivedSykmelding(
                            "1",
                            sykmelding =
                                generateSykmelding(
                                    perioder =
                                        listOf(
                                            generatePeriode(
                                                LocalDate.of(2023, 1, 3),
                                                LocalDate.of(2023, 1, 12)
                                            )
                                        ),
                                ),
                        )

                    val overlapper =
                        sykmeldingService.hasOverlappingPeriodsFromRegister(receivedSykmelding)
                    val overlapperInfotrygd =
                        sykmeldingService.hasOverlappingPeriodsFromInfotrygd(
                            receivedSykmelding,
                            infotrygdForesp
                        )
                    overlapper shouldBeEqualTo false
                    overlapperInfotrygd shouldBeEqualTo false
                }
            }
        }
    })
