object Shared {

  val par = {
    val travis = sys.env.getOrElse("TRAVIS", default = "false") == "true"
    if (travis) 2
    else sys.runtime.availableProcessors
  }

}
