package org.squbs.unicomplex.streamCube


import akka.util.ByteString
import spray.http._

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import scala.annotation.tailrec
import spray.http.parser.HttpParser
import scala.collection.mutable.ArrayBuffer
import org.squbs.unicomplex.streamCube.Event.IgnoreAllFurtherInput


/**
 * A true <b>streaming</b> multipart parser implementation.
 * Receives ByteString input in chunks as they arrive.
 * Emits the following events when sufficient facts are available or waits for more data:
 * <ul>
 *  <li>[[org.squbs.unicomplex.streamCube.Event.PartStart]] - part's headers are parsed
 *  <li>[[org.squbs.unicomplex.streamCube.Event.PartContinue]] - a new chunk of data for the current part has arrived
 *  <li>[[org.squbs.unicomplex.streamCube.Event.PartEnd]] - last chunk of the part has arrived
 *  <li>[[org.squbs.unicomplex.streamCube.Event.NeedMoreData]]
 * </ul>
 *
 * Created by yfinkelstein on 6/19/14.
*/
abstract class AbstractParser extends Parser {
  def apply(input: ByteString): Event = parseMessageSafe(input)


  def byteChar(input: ByteString, ix: Int): Char =
    if (ix < input.length) input(ix).toChar else throw NotEnoughDataException

  def asciiString(input: ByteString, start: Int, end: Int): String = {
    val end_ = math.min (input.length, end)
    @tailrec def build(ix: Int = start, sb: JStringBuilder = new JStringBuilder(end_ - start)): String =
      if (ix == end_) sb.toString else build(ix + 1, sb.append(input(ix).toChar))
    if (start == end_) "" else build()
  }

  def parseMessageSafe(input: ByteString, offset: Int = 0): Event = {
    def needMoreData = this.needMoreData(input, offset)(parseMessageSafe)
    if (input.length > offset)
      try parseMessage(input, offset)
      catch {
        case NotEnoughDataException ⇒ needMoreData
        case e: ParsingException    ⇒ Event.ParsingError(e.status, e.info)
      }
    else needMoreData
  }

  def parseMessage(input: ByteString, offset: Int): Event

  def needMoreData(input: ByteString, offset: Int)(next: (ByteString, Int) ⇒ Event): Event =
    if (offset == input.length) Event.NeedMoreData (next(_, 0))
    else Event.NeedMoreData (more ⇒ next(input ++ more, offset))
}

/**
 * Parsing starts with an instance of this class. It can switch to other parsers for various parts of the content
 * @param boundMatcher for multipart boundary matching
 * @param matchOpeningBoundary true when this is called for the very first time when a new multipart request has arrived
 */
class RootParser (val boundMatcher:BoundaryMatcher, val matchOpeningBoundary:Boolean) extends AbstractParser {
  var currentBodyPart:PartDef = null

  def matchOpeningBoundary (input : ByteString, offset: Int) : Int = {
    if (input.length <= boundMatcher.boundLength + 2)
      throw NotEnoughDataException

    //todo skip control characters
    //todo skip one line
    val boundaryStart = input.indexOfSlice (boundMatcher.boundaryBytes, offset)
    if (boundaryStart < 0)
      throw new ParsingException (s"part's preamble must start with declared boundary ${boundMatcher.boundLength}")

    val cursor = boundaryStart + boundMatcher.boundLength
    if (byteChar(input, cursor) == '\r' && byteChar(input, cursor + 1) == '\n')
      cursor + 2
    else
      throw new ParsingException (s"part's boundary must be followed by CRLF")
  }

  type HeaderSeq = ArrayBuffer[HttpHeader] // header accumulator type

  @tailrec private def parseHeaderLine2 (input: ByteString, offset: Int, headerAccumulator: HeaderSeq) : Int = {
    val adjusted_input = input.drop(offset)
    //val tmp = asciiString(adjusted_input, 0, 500)
    val linput = adjusted_input.take(100)
    val colo_idx = linput.indexOf(':')
    if (colo_idx <= 0)
      throw new ParsingException(s"no header separated by ':' found in the first 100 chars of line: ${asciiString(linput, 0, linput.length)}")

    val rinput = adjusted_input.drop(colo_idx + 1).take (1024)
    val newline_idx = rinput.indexOfSlice("\r\n")
    if (newline_idx <=0 )
      throw new ParsingException(s"header value too long: ${asciiString(rinput, 0, rinput.length)}")

    val header = asciiString(linput, 0, colo_idx).trim
    val value = asciiString(rinput, 0, newline_idx).trim

    HttpParser.parseHeader (HttpHeaders.RawHeader(header, value))
      .fold (error => throw new ParsingException (StatusCodes.BadRequest, error), h => headerAccumulator += h)

    val cursor = colo_idx + 1 + newline_idx + 2
    if (byteChar(adjusted_input, cursor) == '\r' && byteChar(adjusted_input, cursor + 1) == '\n')
      offset + cursor + 2
    else
      parseHeaderLine2 (input, offset + cursor, headerAccumulator)
  }

  //process part's headers such as content-disposition
  //this is a slightly faster version of [[parseHeaderLine]] but at a cost of more code
/*
  @tailrec private def parseHeaderLine (input : ByteString, offset: Int, headerAccumulator: HeaderSeq) : Int = {
    var header:String = null
    var value:String = null

    @tailrec def parseHeaderName(ix: Int = 0, sb: JStringBuilder = new JStringBuilder(32)): Int = {
      if (ix > 100)
        throw new ParsingException(s"too long part's header name: ${sb.toString}")

      byteChar(input, offset + ix) match {
        case ':' ⇒
          header = sb.toString.trim
          ix + 1
        case c ⇒ parseHeaderName(ix + 1, sb.append(c))
      }
    }

    @tailrec def parseHeaderValue(ix: Int = 0, sb: JStringBuilder = new JStringBuilder(32)): Int = {
      if (ix > 1024)
        throw new ParsingException(s"too long part's header value: ${sb.toString}")

      byteChar(input, offset + ix) match {
        case '\r' if byteChar(input, offset + ix + 1) == '\n' ⇒
          value = sb.toString.trim
          ix + 2
        case c ⇒ parseHeaderValue(ix + 1, sb.append(c))
      }
    }

    val np = parseHeaderName ()
    val cursor = offset + parseHeaderValue(np)
    assert (header != null)
    assert (value != null)
    HttpParser.parseHeader (HttpHeaders.RawHeader(header, value))
      .fold (error => throw new ParsingException (StatusCodes.BadRequest, error), h => headerAccumulator += h)

    if (byteChar(input, cursor) == '\r' && byteChar(input, cursor + 1) == '\n')
      cursor + 2
    else
      parseHeaderLine (input, cursor, headerAccumulator)
  }
*/
  def parseHeaderLines (input : ByteString, offset: Int) : Event = {
    val headerAccumulator: HeaderSeq = ArrayBuffer.empty
    val cursor = parseHeaderLine2 (input, offset, headerAccumulator) //puts headers in the supplied accumulator

    val disposition = headerAccumulator.collectFirst {
      case cd @ HttpHeaders.`Content-Disposition`(_, _) => cd
    }.getOrElse (throw new ParsingException("Content-Disposition header is missing"))

    val contentType = headerAccumulator.collectFirst {
      case HttpHeaders.`Content-Type`(ct) => ct
    }.getOrElse(ContentTypes.`text/plain`)

    currentBodyPart = PartDef(disposition, contentType)
    Event.PartStart (currentBodyPart, () => {
        val bp = new BodyParser(boundMatcher, currentBodyPart)
        bp (input.drop (cursor))
      })
  }

   override def parseMessage (input : ByteString, offset: Int) : Event = {
    val cursor = if (matchOpeningBoundary)  matchOpeningBoundary(input, offset) else offset
    parseHeaderLines (input, cursor)
  }
}

/**
 * Finds the boundary in the given buffer using Boyer-Moore algorithm.
 * @param boundary the string to look for
 */
class BoundaryMatcher (val boundary: String) {

  def asciiString(input: ByteString, start: Int, end: Int): String = {
    val end_ = math.min (input.length, end)
    @tailrec def build(ix: Int = start, sb: JStringBuilder = new JStringBuilder(end_ - start)): String =
      if (ix == end_) sb.toString else build(ix + 1, sb.append(input(ix).toChar))
    if (start == end_) "" else build()
  }

  val boundaryBytes = ByteString ("--")  ++ ByteString(boundary)
  private val bcs: Array[Int] = new Array[Int](128)
  private val gss: Array[Int] = new Array[Int](boundaryBytes.length)
  compileBoundaryPattern()

  def toStr = asciiString (boundaryBytes, 0, boundaryBytes.length)
  def boundLength = boundaryBytes.length

  private def compileBoundaryPattern() {
    // Pre-calculate part of the bad character shift
    // It is a table for where in the pattern each
    // lower 7-bit value occurs
    boundaryBytes.zipWithIndex.foreach ((el) => bcs(el._1 & 0x7f) = el._2 + 1)

    def fillLoop (p : Int) {
      for (j <- (p to boundaryBytes.length - 1).reverseIterator) {
        if (boundaryBytes(j) == boundaryBytes(j - p)) {
          gss(j - 1) = p
        } else
          return
      }
      // if we are here j == p

      // This fills up the remaining of optoSft
      // any suffix can not have larger shift amount
      // then its sub-suffix. Why???
      for (j <- 0 to (p-1)) gss(j) = p
    }
    for (i <- (1 to boundaryBytes.length).reverseIterator) fillLoop(i)

    // Set the guard value because of unicode compression
    gss (boundaryBytes.length -1) = 1

  }

  /**
   * @return -1 if there is no match or index where the match starts
   */
   def apply (input : ByteString, offset: Int): Int = {
    /**
     *
     * @param off offset to input to try matching from
     * @return offset to input where match occurred or -1 if match failed
     */
    @tailrec def tryFrom (off : Int) : Int = {
      if (off >= input.length ) return -1

      @inline def getCharOptimistic (pos : Int) =
        if (off + pos >= input.length) boundaryBytes(pos) else input(off+pos)

      var position = boundaryBytes.length - 1
      var ch = getCharOptimistic(position)
      while (position >= 0 &&  ch == boundaryBytes(position)) {
        position -= 1
        ch = getCharOptimistic(position)
      }
      if (position < 0) // match success
        off
      else
        tryFrom(off + math.max (position + 1 - bcs(ch & 0x7f), gss(position)))
    }
    tryFrom (offset)
  }
}

/**
 * Looks for the closing boundaries of the parts
 * @param boundMatcher
 * @param currentBodyPart
 */
class BodyParser (val boundMatcher:BoundaryMatcher, var currentBodyPart: PartDef) extends AbstractParser {

  override def parseMessage (input : ByteString, offset: Int) : Event = {
    val match_offset = boundMatcher (input, offset)
    if (match_offset >= 0) {
      // need at least boundaryBytes.length + 2 (CRLF or "--") starting from that match_offset position
      if (match_offset > input.length - boundMatcher.boundLength -2) {
        // we have a boundary start suspect and need more bytes to confirm or deny it's a real boundary
        needMoreData (input, offset) {parseMessage}
      } else {
        //there should be CRLF just before the part's closing boundary
        if (input(match_offset -2) != '\r' || input(match_offset-1) != '\n')
          throw new ParsingException (s"part's closing boundary must be preceded by CRLF")

        val emitEndFunc = Event.PartEnd(HttpData (input.slice(offset, match_offset - 2)), currentBodyPart, _:Boolean, _:()=>Event)
        val posAfterBoundary = match_offset + boundMatcher.boundLength
        if (input(posAfterBoundary) == '\r' && input (posAfterBoundary+1) == '\n') //isLastPart = false
          emitEndFunc (false, () => {
            val rp = new RootParser(boundMatcher, false)
            rp(input.drop(posAfterBoundary + 2))
          })
        else if (input (posAfterBoundary) == '-' && input (posAfterBoundary+1) == '-') //isLastPart = true
          emitEndFunc (true, () => IgnoreAllFurtherInput)
        else
          throw new ParsingException (s"part's boundary must be followed by CRLF")
      }

    } else
      Event.PartContinue (HttpData(input.drop(offset)), currentBodyPart, this)

  }
}

// Content-Disposition<? >: form-data; name="payload"; filename="payload"
// http://www.ietf.org/rfc/rfc2388.txt
// http://www.ietf.org/rfc/rfc1867.txt
// http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
// http://tools.ietf.org/html/rfc2183
// http://tools.ietf.org/html/rfc2046#section-5.1.3


/* example

curl --trace tr1.bin -F "payload=@test-payload.json;type=application/json;filename=payload" -F "userid=1" -F "filecomment=This is an image file" -F "image=@/Users/yfinkelstein/Pictures/photo/pictures/IMG00035.jpg" -H "Transfer-Encoding: chunked"  http://localhost:8080/file-upload

results in request payload:


--------------------------6fbacd1205a11390
Content-Disposition: form-data; name="payload"; filename="payload"
Content-Type: application/json

{"key": "a1,
"body":{"a":5, "b":"str1"}
}


--------------------------6fbacd1205a11390
Content-Disposition: form-data; name="userid"

1
--------------------------6fbacd1205a11390
Content-Disposition: form-data; name="filecomment"

This is an image file
--------------------------6fbacd1205a11390
Content-Disposition: form-data; name="image"; filename="IMG00035.jpg"
Content-Type: image/jpeg

<FF><D8><FF><E1>^A,Exif^@^@II*^@^H^@^@^@
 */
