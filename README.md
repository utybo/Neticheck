# Neticheck: EPITA Netiquette Checker

> Fail netiquette

Tired of receiving this reply? This small project allows you to see whether your email complies with EPITA's Netiquette 
almost instantly. Results are printed in color in the console in an edible format, and also give the related section in
the Netiquette.

![screenshot](https://cdn.discordapp.com/attachments/535239062139699201/570752869104484356/unknown.png)

Neticheck can also produce JSON files containing the results of the analysis. These files can then be read by Neticheck
using the `-i` option.

This is my first project in Kotlin, feel free to suggest improvements to the code.

This app uses:

* Apache James Mime4J for parsing EMails
* Jansi for writing colors to the terminal
* Argparser for parsing console arguments
* Gson for creating and parsing .json files

This project is made available to you under the MPL 2.0 license (incompatible with secondary licenses).

`.jar` files are coming soon, if I find enough motivation

### Command line

Usage: `java -jar Neticheck.jar path/to/my/file.eml`

To get the .eml file: from Thunderbird, choose Save As on any message in your inbox. You can also test your own messages
by saving them as drafts.

To get more information on the different options available: `java -jar Neticheck.jar --help` 

### Severity

* **Errors**: Neticheck has spotted elements that do not correspond to the Netiquette.
* **Warning**: Neticheck has spotted a potential issue. It could be inaccurate, but is still worth having a look at
* **Info**: Neticheck has spotted some potentially dangerous elements, which could be perfectly normal in some cases

### Supported checks

* Content-Type validation (empty, invalid, incorrect `multipart/mixed`)
* Potential post to a restricted newsgroup (e.g. `assistants.news`)
* Potential `Cc`, `Reply-To`, `In-Reply-To` misuse
* Potential missing identity in `From`
* Subject presence
* Subject length
* Subject format (`[TAG1][TAG2] Subject`)
* Tags formatting
* One or zero `Re: `
* Use of English determiners in subject
* Signature presence (both it existing and there only being one signature)
* Signature length (both line length and number of lines)
* Signature separator format
* First line of signature empty
* Body line length
* Message line length
* Trailing whitespace