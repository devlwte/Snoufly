package com.kyrn.snoufly.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyrn.snoufly.data.TranslationMap

// ─── CompositionLocal ────────────────────────────────────────────────────────

val LocalTranslations = staticCompositionLocalOf { TranslationMap() }

// ─── API pública ─────────────────────────────────────────────────────────────

/**
 * Traduce [key] dentro de [section], con soporte para argumentos posicionales
 * ({0}, {1}…). Reactivo: se recompone automáticamente al cambiar el locale.
 *
 * Ejemplo: t("tabs.recents", "Recientes", section = "library")
 */
@Composable
@ReadOnlyComposable
fun t(
    key: String,
    fallback: String,
    section: String = "common",
    vararg args: Any,
): String {
    val sectionMap = LocalTranslations.current.section(section)
    val raw = sectionMap.resolve(key) ?: fallback
    return if (args.isEmpty()) raw else raw.format(*args)
}

///**
// * Devuelve la sección [section] de [translations] (para uso fuera de Composables).
// */
//fun translationSection(
//    translations: TranslationMap,
//    section: String,
//): Map<String, Any> = translations.section(section)

/**
 * Resuelve [key] dentro de [sectionMap] sin necesidad de un Composable.
 */
fun resolveTranslation(
    sectionMap: Map<String, Any>?,
    key: String,
    fallback: String,
): String = sectionMap?.resolve(key) ?: fallback

// ─── Extensiones internas ────────────────────────────────────────────────────

/**
 * Mapea el nombre de sección al mapa correspondiente de [TranslationMap].
 * Centraliza la lógica que antes se repetía en tres funciones distintas.
 */
private fun TranslationMap.section(name: String): Map<String, Any> =
    when (name) {
        "common"      -> common
        "library"     -> library
        "player"      -> player
        "favorites"   -> favorites
        "equalizer"   -> equalizer
        "settings"    -> settings
        "edit_dialog" -> edit_dialog
        "song_item"   -> song_item
        else          -> emptyMap()
    }

/**
 * Navega el árbol de mapas usando puntos como separadores ("tabs.recents").
 * Devuelve null si la ruta no existe.
 */
private fun Map<String, Any>.resolve(key: String): String? =
    key.split(".").fold<String, Any?>(this) { current, segment ->
        (current as? Map<*, *>)?.get(segment)
    }?.toString()

/**
 * Reemplaza {0}, {1}… con los [args] dados.
 * Equivalente a String.format pero usando la convención de llaves del proyecto.
 */
private fun String.format(vararg args: Any): String =
    args.foldIndexed(this) { index, acc, arg ->
        acc.replace("{$index}", arg.toString())
    }