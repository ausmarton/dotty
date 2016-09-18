package dotty.tools
package dotc
package reporting

import scala.collection.mutable
import util.SourcePosition
import core.Contexts._
import Reporter._
import java.io.{ BufferedReader, IOException, PrintWriter }
import scala.reflect.internal.util._
import printing.SyntaxHighlighting._
import printing.Highlighting._
import diagnostic.Message

/**
 * This class implements a more Fancy version (with colors!) of the regular
 * `ConsoleReporter`
 */
class FancyConsoleReporter(
  reader: BufferedReader = Console.in,
  writer: PrintWriter = new PrintWriter(Console.err, true)
) extends ConsoleReporter(reader, writer) {

  def stripColor(str: String): String =
    str.replaceAll("\u001B\\[[;\\d]*m", "")

  def sourceLine(pos: SourcePosition)(implicit ctx: Context): (String, Int) = {
    val lineNum = s"${pos.line}:"
    (lineNum + hl"${pos.lineContent.stripLineEnd}", lineNum.length)
  }

  def columnMarker(pos: SourcePosition, offset: Int)(implicit ctx: Context) =
    if (pos.startLine == pos.endLine) {
      val whitespace = " " * (pos.column + offset)
      val carets =
        Red("^" * math.max(1, pos.endColumn - pos.startColumn))

      whitespace + carets.show
    } else {
      Red(" " * (pos.column + offset) + "^").show
    }

  def errorMsg(pos: SourcePosition, msg: String, offset: Int)(implicit ctx: Context) = {
    var hasLongLines = false
    val leastWhitespace = msg.lines.foldLeft(Int.MaxValue) { (minPad, line) =>
      val lineLength = stripColor(line).length
      val padding =
        math.min(math.max(0, ctx.settings.pageWidth.value - offset - lineLength), offset + pos.startColumn)

      if (padding < minPad) padding
      else minPad
    }

    msg
      .lines
      .map { line => " " * leastWhitespace + line }
      .mkString(sys.props("line.separator"))
  }

  def posStr(pos: SourcePosition, kind: String)(implicit ctx: Context) =
    if (pos.exists) Blue({
      val file = pos.source.file.toString

      val outer = if (pos.outer.exists) {
        s"This location is in code that was inlined at ${pos.outer}:\n" +
        printStr(pos.outer) + "\n" + "-" * ctx.settings.pageWidth.value
      } else ""

      val prefix = s"-- $kind: $file "
      prefix +
      ("-" * math.max(ctx.settings.pageWidth.value - stripColor(prefix).length, 0)) +
      "\n" + outer
    }).show else ""

  /** Prints the message with the given position indication. */
  override def printMessageAndPos(msg: String, pos: SourcePosition, kind: String = "")(implicit ctx: Context): Unit = {
    printMessage(posStr(pos, kind))
    if (pos.exists) {
      val (src, offset) = sourceLine(pos)
      val marker = columnMarker(pos, offset)
      val err = errorMsg(pos, msg, offset)

      printMessage(List(src, marker, err).mkString("\n"))
    } else printMessage(msg)
  }

  override def printExplanation(m: Message)(implicit ctx: Context): Unit = {
    printMessage(hl"""|
                      |${Blue("Explanation")}
                      |${Blue("===========")}""".stripMargin)
    printMessage(m.explanation)
  }


  //override def summary(implicit ctx: Context): String = {
  //  val b = new mutable.ListBuffer[String]
  //  if (warningCount > 0)
  //    b += countString(warningCount, Yellow("warning").show) + " found"
  //  if (errorCount > 0)
  //    b += countString(errorCount, Red("error").show) + " found"
  //  for ((settingName, count) <- unreportedWarnings)
  //    b += s"there were $count ${settingName.tail} ${Yellow("warning(s)").show}; re-run with $settingName for details"
  //  b.mkString("\n")
  //}
}
