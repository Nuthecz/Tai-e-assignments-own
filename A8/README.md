# A8

## 理解

- 根据 [指南](https://github.com/RicoloveFeng/SPA-Freestyle-Guidance/blob/main/assignments/Assignment 8.md) 和 [理解](https://blog.csdn.net/qsort_/article/details/130760134?ops_request_misc=&request_id=&biz_id=102&utm_term=tai-e&utm_medium=distribute.pc_search_result.none-task-blog-2~all~sobaiduweb~default-1-130760134.nonecase&spm=1018.2226.3001.4187) 进行操作
- 主要是对于 Source，Sink 的处理形成 taintFlows，在这个过程中通过 TaintTransfer 构建 TFG 进行污点传播
- 对于 TaintTransfer 的传播，在 Solver 中对于 delta 和方法调用处添加污点传播处理语句，然后在 TaintAnalysiss 中应用污点传播的规则进行处理，实质就是 添加边(taintFlowGraph.addEdge)与 处理后继结点的传播(workList.addEntry)

### 注意

- 这里基于 A6 的 Solver 进行改进，主要是扩展了处理动态调用和静态调用的交集地带，使得可以直接在交集中插入对于污点的处理
- 可以将 方法调用中三种污点传播进行统一，根据参数来决定静态调用和动态调用