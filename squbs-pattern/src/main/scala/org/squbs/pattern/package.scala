package org.squbs

import akka.util.ByteString
import org.zeromq.ZFrame

/**
 * Created by huzhou on 4/3/14.
 */
package object pattern {

  /**
   * ZeroMQ utility
   */
  implicit def byteStringToZFrame(bs:ByteString):ZFrame = new ZFrame(bs.toArray)

  implicit def zFrameToByteString(zf:ZFrame):ByteString = if(zf.hasData) ByteString(zf.getData) else ByteString.empty

}
