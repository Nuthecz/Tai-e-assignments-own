# 理解

## analysis

- `pascal.taie.analysis.dataflow.analysis.DataflowAnalysis`
  - 这是一个抽象的数据流分析类，是**具体的数据流分析与求解器之间的接口**。一个具体的数据流分析(如活跃变量分析)需要实现它的接口，而求解器(如迭代求解器)需要通过它的接口来求解数据流。
  - 它是一个 `interface` , 其中包含`newBoundaryFact`,`newInitialFact`,`meetInto`,`transferNode`的实现声名
  - 包含`<Node>`类型, 它表示 CFG 的 BB
  - 包含`<Fact>`类型, 它表示数据流分析中用来描述程序状态的元素, 即有关程序代码的信息或属性。它与Node联系在一起，表示一个{name: information}的展示

- `pascal.taie.analysis.dataflow.analysis.LiveVariableAnalysis`
  - 这个类通过实现 `DataflowAnalysis` 的接口来定义具体的活跃变量分析。
  - LiveVariableAnalysis 继承抽象类 AbstractDataflowAnalysis, AbstractDataflowAnalysis 继承接口 DataflowAnalysis
- `pascal.taie.analysis.dataflow.fact.SetFact<Var>`
  - 这个泛型类用于把 data fact 组织成一个集合。
- `pascal.taie.analysis.dataflow.fact.DataflowResult`
  - 该类对象用于维护数据流分析的 CFG 中的 fact。
- `pascal.taie.analysis.graph.cfg.CFG`
  - 这个类用于表示程序中方法的控制流图(control-flow graphs)。
- `pascal.taie.analysis.dataflow.solver.Solver`
  - 这是数据流分析求解器的基类，包含了求解器的抽象功能。
- `pascal.taie.analysis.dataflow.solver.IterativeSolver`
  - 这个类扩展了 `Solver` 的功能并实现了迭代求解算法。

## ir

- `pascal.taie.ir.exp.Exp`
  - 这是 Tai-e 的 IR 中的一个关键接口，用于表示程序中的所有表达式。
- `pascal.taie.ir.stmt.Stmt`
  - 这是 Tai-e 的 IR 中的另一个关键接口，它用于表示程序中的所有语句。
- **注意区分表达式(expressions)与语句(statements)的区别**: 
  - 语句是执行特定操作的指令(if,for,while等,只操作通常不返回)
  - 表达式是一个计算出一个值的代码片段(存在计算值并返回)

## 流程理解

- 对于整个流程，就是使用 `DataflowAnalysis` 与 `Solver` 进行交互。
  - `DataflowAnalysis` 对数据流进行处理, 使用子类 LiveVariableAnalysis 对基类的功能进行扩展, 实现了分析流中的初始化, Transfer Function 与 Control Flow 的实现。
  - `Solver` 实现了迭代解释器, 它通过子类 lterativeSolver 对基类功能进行实现。之后通过 cfg 表示当前的控制流, 然后使用由 DataflowAnalysis 定义的 analysis 来对 cfg 进行操作。
    - 这里 `cfg` 是对于 Node 进行各项操作, 比如判断是否 Exit, 遍历寻找所有 BB。
    - 而 `analysis` 则是包含了所有对于数据流的操作，比如 meetInto, transferNode。 
