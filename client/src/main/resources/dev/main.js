import { scastie } from 'scalajs:main.js'
import '@resources/sass/app-main.scss'
import * as Treesitter from 'web-tree-sitter'

window.ScastieSettings = {
  defaultServerUrl: ""
};

window.scastie = scastie;
window.Treesitter = Treesitter;

scastie.ScastieMain.main();
