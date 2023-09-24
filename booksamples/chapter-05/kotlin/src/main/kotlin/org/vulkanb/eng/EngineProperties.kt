package org.vulkanb.eng

import java.util.*

object EngineProperties {
    private const val DEFAULT_UPS = 30
    private const val FILENAME = "eng.properties"
    private const val DEFAULT_REQUESTED_IMAGES = 3

    val ups: Int
    val validate: Boolean
    val physDeviceName : String?
    val requestedImages: Int
    val vSync: Boolean

    init {
        val props = Properties()
        EngineProperties::class.java.getResourceAsStream("/$FILENAME").use { stream ->
            props.load(stream)
            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("vkValidate", false).toString().toBoolean()
            physDeviceName = props.getProperty("physDeviceName")
            requestedImages = props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString().toInt()
            vSync = props.getOrDefault("vsync", true).toString().toBoolean()
        }
    }
}
