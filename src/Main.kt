/*
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.

  This Source Code Form is "Incompatible With Secondary Licenses", as
  defined by the Mozilla Public License, v. 2.0.
 */

package guru.zoroark.neticheck

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import java.io.FileInputStream

@Suppress("KDocMissingDocumentation")
class NeticheckArgs(parser: ArgParser)
{
    val b: Boolean by parser.flagging("Enable body-only file")

    val source: String by parser.positional("Source .eml file (or .txt if -b is set)")
}

/**
 * Main entry point
 */
fun main(args: Array<String>): Unit = mainBody(programName = "Neticheck") {
    ArgParser(args).parseInto(::NeticheckArgs).run {
        AnsiConsole.systemInstall()

        val list = mutableListOf<NetiquetteHint>()
        @Suppress("RedundantElseInIf")
        if (b)
        {
            TODO()
        }
        else
        {
            checkEml(FileInputStream(source), list)
        }
        println(ansi() fg Ansi.Color.GREEN str "Check finished with " + list.size + " hint(s)" reset Unit)
        for (hint in list)
        {
            println((ansi() fg colorOf(hint.type) str "[${hint.type.symbol}] ${hint.message}" resetStr Unit) +
                    if (hint.reference != null)
                        " (see ${hint.reference})"
                    else "")
            if(hint.context != null)
            {
                println(" |- context:")
                for(s in hint.context.trim().chunkedSequence(60))
                {
                    println(" |  | $s")
                }
            }
        }

        println()
        println("-- Report by Neticheck @ zoroark.guru")

        AnsiConsole.systemUninstall()
    }

}

@Suppress("UNUSED_PARAMETER")
private infix fun Ansi.resetStr(unit: Unit): String = this.reset().toString()

private fun colorOf(type: HintType): Ansi.Color = when(type)
{
    HintType.ERROR -> Ansi.Color.RED
    HintType.WARNING -> Ansi.Color.YELLOW
    HintType.INFO -> Ansi.Color.CYAN
}

private infix fun Ansi.str(a: Any): Ansi = this.a(a)

@Suppress("UNUSED_PARAMETER")
private infix fun Ansi.reset(a: Any): Ansi = this.reset()

private infix fun Ansi.fg(color: Ansi.Color): Ansi = this.fg(color)