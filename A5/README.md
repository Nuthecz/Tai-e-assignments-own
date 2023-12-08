# A5

## 理解

跟着这个[指南](https://github.com/RicoloveFeng/SPA-Freestyle-Guidance/blob/main/assignments/Assignment%205.md)做的，很详细

### 注意

- 静态方法和实例方法的区别只有两个，因此可以把相同之处使用另一个方法封装。这里注意静态方法需要根据方法签名找到具体方法
- 注意 `AddReachable` 和 `Analysis` 中的 `AddEdge` 不太一样，前者需要 `o_i` 和 `f` 组成 `o_i.f` ，需要使用 InstanceField 方法；而后者可以直接对于 statement 的左右变量进行操作。
  - 这里由于 `AddReachable` 处理的没有涉及对象的(包含静态方法，静态字段，new和copy)，所以只需要statement就可以解决。
  - 而 `Analysis` 中则需要解决需要实例化的情况(实例方法，字段，数组)，需要与对象进行连接，及两个参数