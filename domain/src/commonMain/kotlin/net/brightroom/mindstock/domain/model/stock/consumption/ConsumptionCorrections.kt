package net.brightroom.mindstock.domain.model.stock.consumption

class ConsumptionCorrections(private val list: List<ConsumptionCorrection>) {
    fun latest(): ConsumptionCorrection? = list.maxByOrNull { it.correctedAt() }

    fun asList(): List<ConsumptionCorrection> = list.toList()
}
