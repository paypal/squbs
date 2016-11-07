/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
