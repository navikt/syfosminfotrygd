package no.nav.syfo.rules.common

import no.nav.syfo.model.juridisk.JuridiskHenvisning

sealed interface Juridisk

object UtenJuridisk : Juridisk

class MedJuridisk(val juridiskHenvisning: JuridiskHenvisning) : Juridisk
