/*
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.

  This Source Code Form is "Incompatible With Secondary Licenses", as
  defined by the Mozilla Public License, v. 2.0.
 */

package guru.zoroark.neticheck

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.DefaultHelpFormatter
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@Suppress("KDocMissingDocumentation")
class NeticheckArgs(parser: ArgParser)
{
    val recursive: Boolean by parser.flagging(
        "-r",
        "--recursive",
        help = "Enable recursion (scanning for .eml files in folders specified in SOURCE)"
    )

    val silent: Boolean by parser.flagging("-s", "--silent", help = "Disable console output")


    val output: File? by parser.storing(
        "-o",
        "--out",
        help = "Path for output to a .json file with the results"
    ) { File(this) }.default { null }

    val import: File? by parser.storing(
        "-i",
        "--import",
        help = "Import results from a .json file. These results will then be merged with the results from " +
                "the files chosen in SOURCE. Do not specify anything in SOURCE if you wish to only see the results" +
                "from the imported file"
    ) { File(this) }.default { null }

    val source: List<File> by parser.positionalList(
        "SOURCE",
        sizeRange = 0..Int.MAX_VALUE,
        help = "Source .eml files (and folders if -r is set)"
    ) {
        File(this)
    }
}

/**
 * Main entry point
 */
fun main(args: Array<String>): Unit = mainBody(programName = "Neticheck") {
    ArgParser(args, helpFormatter = DefaultHelpFormatter(prologue =
    """Neticheck is a simple tool for checking whether emails (in .eml file format) conform to the Netiquette. It can
        |be used to see the results directly or can store the results in a .json file and print the results from these
        |.json files.""".trimMargin()))
        .parseInto(::NeticheckArgs).run {

        val metaHints = mutableListOf<AnalysisHint>()
        val list = if (import != null)
        {
            try
            {
                import!!.fromJson<List<AnalysisResult>>().toMutableList()
            } catch (e: Exception)
            {
                metaHints += HintType.ERROR with "Error on import: " + e.message
                mutableListOf<AnalysisResult>()
            }
        } else
        {
            mutableListOf()
        }

        fun processFile(file: File)
        {
            val hintsForFile = mutableListOf<AnalysisHint>()
            checkEml(FileInputStream(file), hintsForFile)
            list += AnalysisResult(file.name, hintsForFile)
        }

        for (file in source)
        {
            if (file.isDirectory)
                if (recursive)
                    for (subfile in file.walk())
                    {
                        if (subfile.isFile && subfile.extension == "eml")
                            processFile(subfile)
                    }
                else
                    metaHints += HintType.WARNING with "Tried to process a directory without -r option" ctx file.path
            else
                processFile(file)
        }


        if (!silent)
        {
            AnsiConsole.systemInstall()
            for ((name, hints) in list)
            {
                println(ansi() fg Ansi.Color.GREEN str "File $name" reset Unit)
                printHints(hints)
                println()
            }


            println()
            println("-- Report by Neticheck @ zoroark.guru")

            AnsiConsole.systemUninstall()
        }

        output?.writeText(list.toJson(true), charset = StandardCharsets.UTF_8)
    }

}

fun printHints(hints: List<AnalysisHint>)
{
    val warnCount = hints.filter { it.type == HintType.WARNING }.size
    val infoCount = hints.filter { it.type == HintType.INFO }.size
    val errorCount = hints.filter { it.type == HintType.ERROR }.size

    println(
        ansi() fg Ansi.Color.GREEN str "${hints.size} hint(s) total (" fg Ansi.Color.RED str "$errorCount " +
                "errors " fg Ansi.Color.YELLOW str "$warnCount warnings " fg Ansi.Color.CYAN str "$infoCount info" +
                "" fg Ansi.Color.GREEN str ")" reset Unit
    )
    hints.sortedWith(compareBy({ it.type.priority }, AnalysisHint::message, AnalysisHint::context))
        .forEach(::printHint)
}

fun printHint(hint: AnalysisHint)
{
    println(
        (ansi() fg colorOf(hint.type) str "[${hint.type.symbol}] ${hint.message}" resetStr Unit) +
                if (hint.reference != null)
                    " (see ${hint.reference})"
                else ""
    )
    if (hint.context != null)
    {
        println(" |- context:")
        for (s in hint.context.trim().chunkedSequence(60))
        {
            println(" |  | $s")
        }
    }
}

data class AnalysisResult(
    val info: String,
    val hints: List<AnalysisHint>
)

@Suppress("UNUSED_PARAMETER")
private infix fun Ansi.resetStr(unit: Unit): String = this.reset().toString()

private fun colorOf(type: HintType): Ansi.Color = when (type)
{
    HintType.ERROR -> Ansi.Color.RED
    HintType.WARNING -> Ansi.Color.YELLOW
    HintType.INFO -> Ansi.Color.CYAN
}

private infix fun Ansi.str(a: Any): Ansi = this.a(a)

@Suppress("UNUSED_PARAMETER")
private infix fun Ansi.reset(a: Any): Ansi = this.reset()

private infix fun Ansi.fg(color: Ansi.Color): Ansi = this.fg(color)

private fun Any.toJson(prettyPrint: Boolean): String =
    (if (prettyPrint)
        GsonBuilder().setPrettyPrinting().create()
    else
        Gson()).toJson(this)

private fun Any.toJson(gsonObject: Gson = Gson()): String =
    gsonObject.toJson(this)

private inline fun <reified T> String.fromJson(gsonObject: Gson = Gson()): T = gsonObject.fromJson(this, object: TypeToken<T>() {}.type)

private inline fun <reified T> File.fromJson(charset: Charset = StandardCharsets.UTF_8, gsonObject: Gson = Gson()): T =
    FileReader(this, charset).use { gsonObject.fromJson(it, object: TypeToken<T>() {}.type) }