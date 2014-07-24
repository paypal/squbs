package org.squbs.unicomplex

import spray.http._
import akka.util.ByteString
import spray.json._
import scala.reflect.ClassTag


/**
 * Created by junjshi on 14-7-18.
 */
package object streamCube extends DefaultJsonProtocol with spray.httpx.SprayJsonSupport {
  //import spray.json._

  type Parser = ByteString ⇒ Event
  case class PartDef (disp : HttpHeaders.`Content-Disposition`, contentType:ContentType)

  val PARTNAME_SEGMENT = "segment-"
  val PARTNAME_REQUEST = "request"

  case class AttributeValue (data: String)
  type NVPairs = Map[String, AttributeValue]
  sealed abstract class MetadataOperation (val field:String)
  import MetadataOperation._
  case class SetAttrs (attrs: NVPairs) extends MetadataOperation (SET)
  case class UnsetAttrs (attrs: NVPairs) extends MetadataOperation (UNSET)
  type MetadataOps = List[MetadataOperation]
  case class SegmentRange (start: Int, end: Int)

  sealed abstract class SegmentOperation (val field:String)
  import SegmentOperation._
  case class ModifyMetadata (sKey: String, metadataOps:MetadataOps, partial:Option[Boolean] = Some(true)) extends SegmentOperation(MODIFY)
  case class RemoveSegments (sKeys: Set[String]) extends SegmentOperation (REMOVE)
  case class RemoveSegmentRange (range: SegmentRange) extends SegmentOperation (REMOVE_RANGE)
  case class KeepSegments (sKeys: Set[String]) extends SegmentOperation (KEEP)
  case class KeepSegmentRange (range: SegmentRange) extends SegmentOperation (KEEP_RANGE)
  type SegmentOps = List[SegmentOperation]




  //requests
  sealed abstract class ZMSRequest
  case class Create (mKey:String,
                     expiration:Long,
                     metadataOps: Option[MetadataOps],
                     segmentOps: Option[SegmentOps]) extends ZMSRequest


  //------------ json support -------------------
  implicit val attributeValueFormat = jsonFormat1(AttributeValue)
  implicit val segmentRangeFormat = jsonFormat2 (SegmentRange)

  val defaultSetAttrsFormat = jsonFormat1 (SetAttrs)
  implicit val setAttrsFormat:JsonFormat[SetAttrs] = prefixedJsonFormat (defaultSetAttrsFormat)

  val defaultUnsetAttrsFormat = jsonFormat1 (UnsetAttrs)
  implicit val unsetAttrsFormat:JsonFormat[UnsetAttrs] = prefixedJsonFormat (defaultUnsetAttrsFormat)

  case object MetadataOperation{
    val SET = "set"
    val UNSET = "unset"
  }

  implicit object MetadataOperationFormat extends RootJsonFormat[MetadataOperation] {
    override def read(json: JsValue): MetadataOperation = json.asJsObject.fields.head match {
      case (SET, jsv) => jsv.convertTo[SetAttrs]
      case (UNSET, jsv) => jsv.convertTo[UnsetAttrs]
      case unknown => throw new DeserializationException (s"Unexpected attribute $unknown")
    }
    override def write(obj: MetadataOperation): JsValue = obj match{
      case setAttrs: SetAttrs => setAttrs.toJson
      case unsetAttrs: UnsetAttrs => unsetAttrs.toJson
    }
  }

  val defaultModifyMetadataFormat = jsonFormat3(ModifyMetadata)
  implicit val modifyMetadataFormat:JsonFormat[ModifyMetadata] = prefixedJsonFormat (defaultModifyMetadataFormat)

  val defaultRemoveSegmentsFormat = jsonFormat1(RemoveSegments)
  implicit val removeSegmentsFormat:JsonFormat[RemoveSegments] = prefixedJsonFormat (defaultRemoveSegmentsFormat)

  val defaultKeepSegmentsFormat = jsonFormat1(KeepSegments)
  implicit val keepSegmentsFormat:JsonFormat[KeepSegments] = prefixedJsonFormat (defaultKeepSegmentsFormat)

  val defaultRemoveSegmentRangeFormat = jsonFormat1(RemoveSegmentRange)
  implicit val removeSegmentRangeFormat:JsonFormat[RemoveSegmentRange] = prefixedJsonFormat (defaultRemoveSegmentRangeFormat)

  val defaultKeepSegmentRangeFormat = jsonFormat1(KeepSegmentRange)
  implicit val keepSegmentRangeFormat:JsonFormat[KeepSegmentRange] = prefixedJsonFormat (defaultKeepSegmentRangeFormat)

  case object SegmentOperation {
    val MODIFY = "modify"
    val REMOVE = "remove"
    val REMOVE_RANGE = "removeRange"
    val KEEP = "keep"
    val KEEP_RANGE = "keep"
  }
  implicit object SegmentOperationFormat extends RootJsonFormat[SegmentOperation] {
    val x  = ClassTag[SegmentOperation] _
    val y = x.apply(SegmentOperation.getClass)
    //val z = SegmentOperation.productElement(0)

    override def read(json: JsValue) = json.asJsObject.fields.head match {
      case (MODIFY, jsv) => jsv.convertTo[ModifyMetadata]
      case (REMOVE, jsv) => jsv.convertTo[RemoveSegments]
      case (REMOVE_RANGE, jsv) => jsv.convertTo[RemoveSegmentRange]
      case (KEEP, jsv) => jsv.convertTo[KeepSegments]
      case (KEEP_RANGE, jsv) => jsv.convertTo[KeepSegmentRange]
      case unknown => throw new DeserializationException (s"Unexpected attribute $unknown")
    }

    override def write(obj: SegmentOperation) = obj match {
      case modify: ModifyMetadata => modify.toJson
      case remove: RemoveSegments => remove.toJson
      case removeRange: RemoveSegmentRange => removeRange.toJson
      case keep:   KeepSegments   => keep.toJson
      case keepRange:   KeepSegmentRange   => keepRange.toJson
    }
  }


  //responses
  sealed abstract class ZMSResponse
  case class CreateResult (bytesReceived:Long, parts:List[(String, String)]) extends  ZMSResponse

  implicit val createFormat = jsonFormat4(Create)
  implicit val createResultFormat = jsonFormat2(CreateResult)


  private def prefixedJsonFormat[U <:{val field:String}] (default: RootJsonFormat[U]) = new RootJsonFormat[U] {
    override def read(json: JsValue) = default.read(json)
    override def write(obj: U) = JsObject (obj.field -> default.write(obj))
  }




}



package streamCube{

import spray.http._
import spray.util.SingletonException



private[unicomplex] sealed trait Event
private[unicomplex] object Event {
  case class NeedMoreData (next: Parser) extends Event // this is possible when the preamble of a part is not complete

  case class PartStart    (part : PartDef, continue: () ⇒ Event) extends Event //has empty part and headers
  case class PartContinue (data : HttpData, part: PartDef, next: Parser) extends Event //the part is a chunk of data
  case class PartEnd      (data : HttpData, part: PartDef, isLastPart : Boolean, continue: () ⇒ Event) extends Event //a final chunk of data

  case class ParsingError(status: StatusCode, info: ErrorInfo) extends Event
  case object IgnoreAllFurtherInput extends Event with Parser { def apply(data: ByteString) = this }
}

class ParsingException(val status: StatusCode, val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
  def this(status: StatusCode, summary: String = "") =
    this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))
  def this(summary: String) =
    this(StatusCodes.BadRequest, ErrorInfo(summary))
}

private [unicomplex] object NotEnoughDataException extends SingletonException
}



