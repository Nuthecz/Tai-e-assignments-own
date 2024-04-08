# A6

## 理解

跟着这个[指南](https://github.com/RicoloveFeng/SPA-Freestyle-Guidance/blob/main/assignments/Assignment%206.md)修修补补A5的代码，然后根据指针分析添加三个Select的语句

### 注意

- 对于 `visit(New)` 中的 `add <c: x, {c: o_i}> to Wl` 中 `c:x` 和 `c:o_i` 的上下文不是同一个东西，对于 k-limits 的CS场景，`o_i` 的 c 是 k-1 的，也就是说它是堆抽象的上下文。
  - 例如对于 2-CallSite Sensitive 当 `c:m` 的 c 是 [l1,l2] 时
  - `c:x` 中的 c -> [l1,l2]
  - `c:0_i`中的c -> [l2]
- 所以 Select 中会存在一个 `selectHeapContext` 方法，对于处理 c:o_i 需要使用 `contextSelector.selectHeapContext`。