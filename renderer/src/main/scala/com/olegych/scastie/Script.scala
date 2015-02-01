package com.olegych.scastie

//copied from sbt.Script
object Script {
  final case class Block(offset: Int, lines: Seq[String])
  def blocks(lines: Seq[String]): List[Block] = {
    def blocks(b: Block, acc: List[Block]): List[Block] =
      if (b.lines.isEmpty)
        acc.reverse
      else {
        val (dropped, blockToEnd) = b.lines.span { line => !line.startsWith(BlockStart)}
        val (block, remaining) = blockToEnd.span { line => !line.startsWith(BlockEnd)}
        val offset = b.offset + dropped.length
        blocks(Block(offset + block.length, remaining), Block(offset, block.drop(1)) :: acc)
      }
    blocks(Block(0, lines), Nil)
  }
  private val BlockStart = "/***"
  private val BlockEnd = "*/"
}
