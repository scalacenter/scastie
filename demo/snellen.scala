Html{
  val chart = 
    List(
      ("E", 267),
      ("FP", 133),
      ("TOZ", 93),
      ("LPED", 69),
      ("PECFD", 54),
      ("EDFCZP", 40),
      ("FELOPZD", 34),
      ("DEFPOTEC", 27),
      ("LEFODPCT", 20),
      ("FDPLTCEO", 17),
      ("PEZOLCFTD", 13)
    ).map{ case (l, x) =>
      val m = x / 3
      val ls = l.toList.map(c => 
        s"""<p style="margin: 20px ${m}px 20px ${m}px">$c</p>"""
      ).mkString

      s"""<div style="font-size: ${x}px">$ls</div>"""
    }.mkString

  s"""
  <style>
  @font-face {
      font-family: snellen;
      src: url(https://github.com/denispelli/Eye-Chart-Fonts/raw/master/Pelli-EyeChart_11hf.otf);
  }
  .snellen-chart {
    text-align: center;
    font-family: snellen
  }
  </style>

  <div class="snellen-chart">$chart</div>
  
  """
}.fold

