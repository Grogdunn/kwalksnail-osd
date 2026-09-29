package dev.grogdunn.kwalksnailosd.osd

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