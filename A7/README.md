# A7

## 理解

跟着这个 [指南](https://github.com/RicoloveFeng/SPA-Freestyle-Guidance/blob/main/assignments/Assignment%206.md) 和 [经验](https://blog.csdn.net/m0_53632564/article/details/127255320) 做的。主要就是下面的两条规则

- 对于 load，把 **对应** 的 store 的 rhs 用 meet 计算出来，赋给 load 的 lhs
- 对于 store，若 y.f 的值有变化，把 **对应** 的 load 加入 worklist

### 注意

- 这里主要修改的就是 `InterConstantPropagation.java` 类中的方法 `initialize` 和 `transferNonCallNode`，其余的内容可以直接从 A4 和 A6 的代码中拿过来，不需要进行变动。
- 这里的别名分析主要就是对于过程内的 FieldStore，FieldLoad，ArrayStore，ArrayLoad 进行存在别名情况的额外分析，因此在初始化中增加了别名相关的存储数组，表名一个对象的别名信息，然后在处理这些别名的 nonCallNode 方法中添加分析别名数据传播的语句，就是根据上面对于 load 和 store 的理解来进行相关操作，而对于其余的语句，还是以往的操作不作变化。
- 这里注意根据手册中的常量化处理 `canHoldInt` 来进行操作，但是这只是对于 load 相关语句而言，对于 store 相关语句不需要这个，即不需要判断 `a.f` 是否为一个常量