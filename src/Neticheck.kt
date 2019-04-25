/*
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.

  This Source Code Form is "Incompatible With Secondary Licenses", as
  defined by the Mozilla Public License, v. 2.0.
 */

package guru.zoroark.neticheck

import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.dom.Message.Builder
import org.apache.james.mime4j.dom.Multipart
import org.apache.james.mime4j.dom.TextBody
import java.io.InputStream

/**
 * A list of common English delimiters which should be avoided in the subject of the message
 */
private val englishDeterminers = listOf(
    "the", "a", "an", "this", "that", "these", "those", "my", "your", "his", "her", "its",
    "our", "their", "much", "many", "most", "some", "any", "enough"
)

private val restrictedNewsgroups = listOf(
    "announcement.ing1",
    "announcement.ing2",
    "announcement.ing3",
    "announcement.sup",
    "announcement.spe",
    "announcement.vie-etudiante",
    "assistants.apprentis.news",
    "assistants.news",
    "cri.news"
)

/**
 * Check the email for potential breaches of Netiquette
 *
 * @param emlContent An InputStream from which to read the mail's content
 * @param hints The list that will receive all of the hints found by analyzing the mail
 */
fun checkEml(emlContent: InputStream, hints: MutableList<AnalysisHint>)
{
    val msg = Builder.of(emlContent).build()

    checkMeta(msg, hints)

    if (!msg.hasHeader("Content-Type"))
        hints += HintType.ERROR with "No Content-Type specified: assuming text/plain" ref "2.2.2.2"

    val contentType = msg.mimeType
    var body: String? = null
    when
    {
        contentType eqic "text/plain" ->
        {
            val reader = (msg.body as TextBody).reader
            body = reader.readText()
            reader.close()
        }
        contentType eqic "multipart/mixed" ->
        {
            val multipart = msg.body as Multipart
            for ((i, part) in multipart.bodyParts.withIndex())
            {
                if (part.mimeType.startsWith("text/plain", true))
                {
                    if (i != 0)
                    {
                        hints += HintType.ERROR with "Body is not first part in multipart" ref "2.2.2.2"
                    }
                    body = "$part"
                    break
                }
            }
            if (body == null)
                hints += HintType.ERROR with "No message body in multipart" ref "2.2.2.2"
        }
        contentType eqic "application/pgp-signature" ->
            hints += HintType.INFO with "Cannot lint message: PGP Signatures are not supported"
        else -> hints += HintType.ERROR with "Invalid Content-Type" ref "2.2.2.2"
    }

    if (msg.subject != null)
        checkSubject(msg.subject, hints)
    else
        hints += HintType.ERROR with "No subject"

    if (body != null)
        checkBody(body, hints)

}

/**
 * Shortcut infix function for "equals ignore case"
 */
private infix fun String.eqic(s: String): Boolean = this.equals(s, true)

/**
 * Check the metadata of a message (checks its headers for potential issues)
 */
private fun checkMeta(msg: Message, hints: MutableList<AnalysisHint>)
{
    msg.getHeader("Newsgroups")?.doForAll({ it in restrictedNewsgroups }) {
        hints += HintType.WARNING with "This message is bound for a restricted newsgroup, are you sure that you are allowed to do that?" ctx it
    }

    if (msg.hasHeader("Cc"))
        hints += HintType.INFO with "This message has a Cc field: check the recipients carefully" ref "2.1.2" ctx msg.getHeader(
            "Cc"
        )?.firstOrNull()

    if (msg.hasHeader("Reply-To"))
        hints += HintType.INFO with "This message has a Reply-To field: check the address carefully" ref "2.1.2" ctx msg.getHeader(
            "Reply-To"
        )?.firstOrNull()

    if (msg.hasHeader("In-Reply-To"))
        hints += HintType.INFO with "This message has an In-Reply-To field: check that the original message's id is correct" ref "2.1.2" ctx msg.getHeader(
            "Reply-To"
        )?.firstOrNull()

    if (msg.getHeader("From")?.none { it.contains("@epita.fr") } == true)
        hints +=
            HintType.WARNING with "The From field does not contain an address with the epita.fr domain, make sure to include your login or your surname and first name" ref "2.1.3" ctx msg.getHeader(
                "From"
            )?.first()

}

private fun Message.hasHeader(s: String): Boolean = this.header.getFields(s)?.isNotEmpty() ?: false

private fun Message.getHeader(s: String): Iterable<String>? = this.header.getFields(s).map { it.body }

/**
 * Check the subject of a message for potential errors
 */
fun checkSubject(subject: String, hints: MutableList<AnalysisHint>)
{
    if (subject.length > 80)
        hints += HintType.ERROR with "Subject is too long (> 80 chars)" ref "2.1.1.2"

    val split = subject.split(' ', limit = 2)

    if (split.size == 1)
    {
        hints += HintType.ERROR with "Malformed subject" ref "2.1.1"
    }

    checkSubjectTags(split[0], hints)

    if (subject.containsAny(englishDeterminers))
        hints += HintType.WARNING with "Determiners should be removed" ref "2.1.1.2" ctx subject

}

private val trailingWhitespace = Regex(""".*?\s""", RegexOption.MULTILINE)

fun checkBody(body: String, hints: MutableList<AnalysisHint>)
{
    val bodyBits = body.split("\r\n\r\n-- \r\n")

    when
    {
        bodyBits.size < 2 -> hints += HintType.ERROR with "No signature detected" ref "2.3"
        bodyBits.size > 2 -> hints += HintType.ERROR with "Too many signatures, make sure the string '\\r\\n\\r\\n-- \\r\\n' only appears once" ref "2.3"
        else ->
        {
            val signature = bodyBits[1]
            val signatureLines = signature.split("\r\n")

            signatureLines.doForAll({ it.length > 80 }) {
                hints += HintType.ERROR with "Signature line is too long (> 80)" ref "2.3" ctx it
            }

            if (signatureLines.size > 4)
                hints += HintType.ERROR with "Signature size is too long (> 4)" ref "2.3"

            if (signatureLines.isEmpty())
                hints += HintType.ERROR with "Signature is empty" ref "2.3"
            else if (signatureLines[0].isBlank())
                hints += HintType.ERROR with "First line of signature is empty" ref "2.3"

        }
    }

    val lines = bodyBits[0].split("\r\n")

    lines.doForAll({ it.length > 80 }) {
        hints += HintType.ERROR with "Body line is too long (> 80)" ref "2.2.2.1" ctx it
    }

    lines.doForAll({ it.length > 72 && !it.startsWith(">") }) {
        hints += HintType.ERROR with "Message line is too long (> 72)" ref "2.2.2.1" ctx it
    }

    lines.doForAll({ trailingWhitespace.matches(it) && it != "-- " }) {
        hints += HintType.ERROR with "Body line has a trailing whitespace" ref "2.2.2.5" ctx it
    }


    // Some additional checks
    if (body.contains("\r\n--\r\n"))
        hints += HintType.WARNING with "Possibly missing space at end of signature separator" ref "2.3"


    if (Regex("""[^\r][^\n]\r\n-- ?\r\n""").containsMatchIn(body))
        hints += HintType.WARNING with "Possibly missing newline before signature" ref "2.3"

}

/**
 * Execute a function for the first value of the collection that matches the predicate, and return this function's result.
 * If no elements match the predicate, do not do anything and return null.
 */
fun <T, R> Iterable<T>.doForFirst(predicate: (T) -> Boolean, action: (T) -> R): R? =
    doIfNotNull(this.firstOrNull(predicate), action)

fun <T> Iterable<T>.doForAll(predicate: (T) -> Boolean, action: (T) -> Unit): Unit =
    this.filter(predicate).forEach(action)


/**
 * Execute a function if the value is not null and return its result, or else do not do anything and return null
 */
fun <T, R> doIfNotNull(value: T?, func: (T) -> R): R?
{
    if (value != null)
        return func(value)
    return null
}

private fun CharSequence.containsAny(list: List<CharSequence>): Boolean = list.any { this.containsWord(it, true) }

private fun CharSequence.containsWord(word: CharSequence, ignoreCase: Boolean = false): Boolean =
    this.split(' ').any { it.equals("$word", ignoreCase = ignoreCase) }


private val tagsPattern = Regex("""(Re: )?(\[[A-Z\d-_+/]+]){2}""")
private val tooManyRes = Regex("""(Re: ?){2,}""")
private val caseMismatch = Regex("""(\[.*?[^A-Z\d-_+/].*?])""")

fun checkSubjectTags(tags: String, hints: MutableList<AnalysisHint>)
{
    if (!tags.matches(tagsPattern))
    {
        hints += HintType.WARNING with "Tags mismatch, too many tags or no tags detected" ref "2.1.1" ctx tags
        if (tooManyRes.containsMatchIn(tags))
            hints += HintType.ERROR with "Too many Re: " ref "2.1.1.3" ctx tags
        if (caseMismatch.containsMatchIn(tags))
            hints += HintType.ERROR with "Disallowed character in tags" ref "2.1.1.1" ctx tags

    }

    if (tags.contains("[MISC]", true))
    {
        hints += AnalysisHint(HintType.INFO, "MISC tag use is discouraged", "2.1.1.1") ctx tags

    }

}

/**
 * A hint that the Netiquette might not have been respected at some point.
 */
data class AnalysisHint(
    /**
     * The type (or severity) of the hint
     */
    val type: HintType,
    /**
     * The message of the hint, which describes what the hint is
     */
    val message: String,
    /**
     * A reference to either a section of the Netiquette
     */
    val reference: String? = null,
    /**
     * The context of the hint, usually the string that caused this hint to be reported in the first place
     */
    val context: String? = null
)
{
    infix fun ref(s: String?): AnalysisHint = this.copy(reference = s)
    infix fun ctx(s: String?): AnalysisHint = this.copy(context = s)
}

enum class HintType(val symbol: Char, val priority: Int)
{
    INFO('i', 10),
    WARNING('!', 5),
    ERROR('X', 1);

    infix fun with(s: String): AnalysisHint = AnalysisHint(this, s)
}
