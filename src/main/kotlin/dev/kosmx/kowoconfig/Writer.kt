package dev.kosmx.kowoconfig

class Writer(private val builder: StringBuilder = StringBuilder()): CharSequence by builder {
    private var indentLevel = 0

    private fun beginLine(text: CharSequence) {
        builder.append(" ".repeat(indentLevel * 4)).append(text)
    }

    private fun line(text: CharSequence) {
        val indentChange = text.sumOf {
            when(it) {
                '{' -> 1
                '}' -> -1
                else -> 0
            }.toInt()
        }
        if (indentChange < 0) indentLevel += indentChange
        beginLine(text)
        if (indentChange > 0) indentLevel += indentChange

        builder.appendLine()
    }

    operator fun plusAssign(text: String) {
        for (line in text.lines()) {
            this.line(line.trim())
        }
    }

    operator fun inc(): Writer {
        this += "{"
        indentLevel++
        return this
    }
    operator fun dec(): Writer {
        indentLevel--
        this += "}"
        return this
    }

    override fun toString(): String = builder.toString()
    fun nl() {
        builder.appendLine()
    }
}