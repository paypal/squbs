object Shared {

  val par = {
    val ci = sys.env.getOrElse("CI", default = "false") == "true"
    if (ci) 2
    else sys.runtime.availableProcessors
  }

}
