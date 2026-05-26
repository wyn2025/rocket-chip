// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.util

import chisel3._
import chisel3.util._

import chisel3.{Data, SyncReadMem, Vec}
import chisel3.util.log2Ceil

// 插入DPI-C
class read_ram_data extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val addr = Output(UInt(32.W))
    val data  = Input(UInt(32.W))
  })
  setInline("read_ram_data.v",
    """module read_ram_data(input [31 : 0] data, output [31 : 0] addr);
      |  export "DPI-C" function read_ram_data;
      |  function int read_ram_data(input int addr);
      |    // 这里可以加边界检查
      |    if (addr >= 0 && addr <= 2047)
      |      return data;
      |    else
      |      return 32'hDEADBEEF; // 错误码
      |  endfunction
      |endmodule
      |""".stripMargin)
}


object DescribedSRAM {
  def apply[T <: Data](
    name: String,
    desc: String,
    size: BigInt, // depth
    data: T
  ): SyncReadMem[T] = {
// SyncReadMem 不是普通的 Scala 对象，而是 Chisel 标准库里的一个硬件原语（primitive），专门表示"同步读存储器"
    val mem = SyncReadMem(size, data)

    mem.suggestName(name)

    val monitor = Module(new read_ram_data()) 
      monitor.io.data  := mem.read(monitor.io.addr, true.B).asUInt

    val granWidth = data match {
      case v: Vec[_] => v.head.getWidth
      case d => d.getWidth
    }

    val uid = 0
// 向 FIRRTL（Chisel 的中间表示）写入注解元数据，描述 SRAM 的特性，如名称、地址宽度、数据宽度、深度、描述信息和写掩码粒度。这些注解可以被后续的工具链使用，例如生成文档或进行优化。
    Annotated.srams(
      component = mem,
      name = name,
      address_width = log2Ceil(size),
      data_width = data.getWidth,
      depth = size,
      description = desc,
      write_mask_granularity = granWidth
    )
//返回创建好的 SyncReadMem 对象，供调用方连接读写端口。
    mem
  }
}
