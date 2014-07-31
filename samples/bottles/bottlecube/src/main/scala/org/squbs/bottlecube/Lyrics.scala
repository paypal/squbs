package org.squbs.bottlecube

object Lyrics {
  
  def lyric(i: Int) = 
    s"${i bottles} of beer on the wall, ${i bottles} of beer.\n" +
    s"Take one down and pass it around, ${(i - 1) bottles} of beer on the wall."
    
  def lyricEnd(start: Int) =
    s"No more bottles of beer on the wall, no more bottles of beer.\n" +
    s"Go to the store and buy some more, ${start bottles} of beer on the wall."
  
  implicit class BottleSupport(val i: Int) extends AnyVal {
    
    def bottles = i match {
      case 0 => "no more bottles"
      case 1 => "1 bottle"
      case _ => s"$i bottles"
    }
  }
}
