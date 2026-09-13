package com.droidforge.gamepadmouse.input

enum class BindingMode { GAMEPAD, MOUSE }

data class ButtonBinding(
    val keyCodes: Set<Int>,
    val action: MouseAction,
    val modes: Set<BindingMode> = setOf(BindingMode.MOUSE),
    val holdDurationMs: Long = 0L,
) {
    fun appliesIn(mode: ServiceMode): Boolean = when (mode) {
        ServiceMode.GAMEPAD -> BindingMode.GAMEPAD in modes
        ServiceMode.MOUSE -> BindingMode.MOUSE in modes
        ServiceMode.KEYBOARD -> false
    }
}

object BindingCodec {
    private const val RECORD_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = "|"
    private const val LIST_SEPARATOR = "+"

    fun encode(bindings: List<ButtonBinding>): String = bindings.joinToString(RECORD_SEPARATOR) { binding ->
        listOf(
            binding.keyCodes.sorted().joinToString(LIST_SEPARATOR),
            binding.action.name,
            binding.modes.sortedBy { it.name }.joinToString(LIST_SEPARATOR) { it.name },
            binding.holdDurationMs.coerceAtLeast(0L).toString(),
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decode(value: String): List<ButtonBinding> {
        if (value.isBlank()) return emptyList()
        if (FIELD_SEPARATOR !in value) return decodeLegacy(value)
        return value.split(RECORD_SEPARATOR).mapNotNull(::decodeRecord)
    }

    private fun decodeRecord(record: String): ButtonBinding? {
        val fields = record.split(FIELD_SEPARATOR)
        if (fields.size != 4) return null
        val keys = fields[0].split(LIST_SEPARATOR).mapNotNull(String::toIntOrNull).toSet()
        if (keys.isEmpty()) return null
        val action = runCatching { MouseAction.valueOf(fields[1]) }.getOrNull() ?: return null
        val modes = fields[2].split(LIST_SEPARATOR)
            .mapNotNull { runCatching { BindingMode.valueOf(it) }.getOrNull() }
            .toSet()
            .ifEmpty { setOf(BindingMode.MOUSE) }
        val hold = fields[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        return ButtonBinding(keys, action, modes, hold)
    }

    private fun decodeLegacy(value: String): List<ButtonBinding> =
        value.split(",").mapNotNull { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val keyCode = parts[0].toIntOrNull() ?: return@mapNotNull null
            val action = runCatching { MouseAction.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            ButtonBinding(setOf(keyCode), action, setOf(BindingMode.MOUSE), 0L)
        }
}

class BindingMatcher {
    private val heldKeys = mutableSetOf<Int>()
    private val firedBindings = mutableSetOf<ButtonBinding>()

    fun keyDown(keyCode: Int) {
        heldKeys += keyCode
    }

    fun keyUp(keyCode: Int) {
        heldKeys -= keyCode
        firedBindings.removeAll { keyCode in it.keyCodes }
    }

    fun matching(bindings: List<ButtonBinding>, mode: ServiceMode): List<ButtonBinding> =
        bindings.filter { binding ->
            binding.appliesIn(mode) &&
                binding.keyCodes.all { it in heldKeys } &&
                binding !in firedBindings
        }.sortedByDescending { it.keyCodes.size }

    fun markFired(binding: ButtonBinding) {
        firedBindings += binding
    }

    fun isHeld(binding: ButtonBinding): Boolean = binding.keyCodes.all { it in heldKeys }
}
