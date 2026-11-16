package dev.grogdunn.kwalksnailosd

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

internal fun uiText(resource: StringResource, vararg args: Any): String =
    runBlocking { getString(resource, *args) }
