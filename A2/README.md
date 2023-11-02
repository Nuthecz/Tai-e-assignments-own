# A2

## 理解

- 这次搞明白代码的实现逻辑，同时跟着指南进行就很清晰地实现代码部分
- 在这里 `ConstantPropagation` 实现的就是数据流的处理, 例如 `meetinto`, `Transfer Function`; 而 `Solver` 和 `WorkListSolver` 就是实现迭代算法的具体流程。这两个就相当于前面的是函数实现，后面就是利用函数来实现一个流程
  - 这里需要注意, `meetinto` 处理的就是 两个 BB 之间数据的流动交汇情况, 这里指对于多个分支对同一个变量的赋值交汇在同一点的处理; 而 `TransferNode` 则是对于 BB 内部数据的处理，可以近似看作对于 Statement 的处理。