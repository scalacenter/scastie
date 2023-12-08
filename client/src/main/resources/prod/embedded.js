import '@resources/sass/embed-main.scss'
import { scastie } from 'scalajs:main.js'
import Treesitter from 'web-tree-sitter'

window.ScastieSettings = {
  defaultServerUrl: "https://scastie.scala-lang.org"
};

window.Treesitter = Treesitter;

export default scastie;
