package com.droidforge.gamepadkeyboard

/**
 * Visual skins for the keyboard. Each preset supplies the full palette; the view
 * renders beveled (vertical-gradient) keycaps in those colors, Steam Deck style.
 */
enum class Skin(
    val label: String,
    val bg: Int,
    val capTop: Int,
    val capBottom: Int,
    val actionTop: Int,
    val actionBottom: Int,
    val legend: Int,
    val dim: Int,
    val selectFill: Int,
    val selectRing: Int,
    val flash: Int,
    val badge: Int,
) {
    STEAM_DECK(
        "Steam Deck",
        bg = 0xFF22271B.toInt(), capTop = 0xFF5A6244.toInt(), capBottom = 0xFF414832.toInt(),
        actionTop = 0xFF4C5339.toInt(), actionBottom = 0xFF373D29.toInt(),
        legend = 0xFFEDEACB.toInt(), dim = 0xFFA8AE8E.toInt(),
        selectFill = 0xFF6B7647.toInt(), selectRing = 0xFFF0EDD2.toInt(),
        flash = 0xFFD9C84E.toInt(), badge = 0xFFE8C838.toInt(),
    ),
    MIDNIGHT(
        "Midnight",
        bg = 0xFF10141C.toInt(), capTop = 0xFF2B3345.toInt(), capBottom = 0xFF1B2130.toInt(),
        actionTop = 0xFF232B3B.toInt(), actionBottom = 0xFF161C29.toInt(),
        legend = 0xFFE6EBF5.toInt(), dim = 0xFF8A93A6.toInt(),
        selectFill = 0xFF33507E.toInt(), selectRing = 0xFFBBD4FF.toInt(),
        flash = 0xFF3D81F6.toInt(), badge = 0xFF6EA8FE.toInt(),
    ),
    PS_BLUE(
        "PS Blue",
        bg = 0xFF0D0F14.toInt(), capTop = 0xFF3A4152.toInt(), capBottom = 0xFF232935.toInt(),
        actionTop = 0xFF2E3543.toInt(), actionBottom = 0xFF1C212B.toInt(),
        legend = 0xFFF2F2F2.toInt(), dim = 0xFF9AA3AF.toInt(),
        selectFill = 0xFF3D81F6.toInt(), selectRing = 0xFFEAF2FF.toInt(),
        flash = 0xFF7FB2FF.toInt(), badge = 0xFF4FD1C5.toInt(),
    ),
    OLIVE_LIGHT(
        "Olive Light",
        bg = 0xFFE9E7D8.toInt(), capTop = 0xFFFDFCF3.toInt(), capBottom = 0xFFDDD9C2.toInt(),
        actionTop = 0xFFEFEDDF.toInt(), actionBottom = 0xFFCFCCB4.toInt(),
        legend = 0xFF3A3E2C.toInt(), dim = 0xFF7A7E66.toInt(),
        selectFill = 0xFFC9CE9E.toInt(), selectRing = 0xFF5C613F.toInt(),
        flash = 0xFFEFD96A.toInt(), badge = 0xFF8C7A1E.toInt(),
    ),
    ;
}
