package dev.grogdunn.kwalksnailosd.osd

import dev.grogdunn.kwalksnailosd.generated.resources.Res
import dev.grogdunn.kwalksnailosd.generated.resources.error_fc_bytes
import dev.grogdunn.kwalksnailosd.uiText

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
                throw IllegalArgumentException(uiText(Res.string.error_fc_bytes))
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