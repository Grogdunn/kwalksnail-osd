package dev.grogdunn.kwalksnailosd.osd

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

class OsdParser {
    private val logger = KotlinLogging.logger {}

    fun parse(selectedOsd: Path): Osd {
        val osdBytes = Files.readAllBytes(selectedOsd)
        val fcBytes = osdBytes.take(4).toByteArray()

        logger.info { }
        return Osd(FcFirmware.getFcFirmware(fcBytes))
    }

    data class Osd(val fcFirmware: FcFirmware)


    enum class FcFirmware {
        BETAFLIGHT,
        INAV,
        ARDUPILOT,
        KISS,
        KISSULTRA,
        UNKNOWN;

        companion object {
            fun getFcFirmware(fcBytes: ByteArray): FcFirmware {
                if (fcBytes.size < 4) {
                    throw IllegalArgumentException("FC bytes must be at least 4 bytes long")
                }
                return getFcFirmware(String(fcBytes, Charsets.UTF_8))
            }


            fun getFcFirmware(osdString: String): FcFirmware {
                return when (osdString) {
                    "BTFL" -> BETAFLIGHT
                    "INAV" -> INAV
                    "ARDU" -> ARDUPILOT
                    "KISS" -> KISS
                    "ULTR" -> KISSULTRA
                    else -> UNKNOWN
                }
            }


        }
    }

}