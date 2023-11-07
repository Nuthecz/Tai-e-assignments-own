# 理解

## analysis

- `pascal.taie.analysis.dataflow.analysis.DataflowAnalysis`
  - 这是一个抽象的数据流分析类，是**具体的数据流分析与求解器之间的接口**。一个具体的数据流分析(如活跃变量分析)需要实现它的接口，而求解器(如迭代求解器)需要通过它的接口来求解数据流。
  - 它是一个 `interface` , 其中包含`newBoundaryFact`,`newInitialFact`,`meetInto`,`transferNode`的实现声名
  - 包含`<Node>`类型, 它表示 CFG 的 BB
  - 包含`<Fact>`类型, 它表示数据流分析中用来描述程序状态的元素, 即有关程序代码的信息或属性。它与Node联系在一起，表示一个{name: information}，即这里的 “facts” 是指分析确定的信息，如在程序某个点活跃的变量集。
- `pascal.taie.analysis.dataflow.analysis.LiveVariableAnalysis`
  - 这个类通过实现 `DataflowAnalysis` 的接口来定义具体的活跃变量分析。
  - LiveVariableAnalysis 继承抽象类 AbstractDataflowAnalysis, AbstractDataflowAnalysis 继承接口 DataflowAnalysis
- `pascal.taie.analysis.dataflow.fact.SetFact<Var>`
  - 这个泛型类用于把 data-flow fact 组织成一个集合。
- `pascal.taie.analysis.dataflow.fact.DataflowResult`
  - 该类对象用于维护数据流分析的 CFG 中的 fact。可以通过它的 API 获取、设置 CFG 节点(BB?)的 `IN facts` 和 `OUT facts`, 这也表示fact存储有关 BB 的信息
- `pascal.taie.analysis.graph.cfg.CFG`
  - 这个类用于表示程序中方法的控制流图(control-flow graphs)。
  - 它是可迭代的，也就是说你可以通过一个 *for* 循环遍历其中的所有节点。这就意味着它维护了某些方法可以遍历所有的节点(CFG继承的接口Graph中方法 `Set<N> getNodes()` 可以遍历所有节点)
- `pascal.taie.analysis.dataflow.solver.Solver`
  - 这是数据流分析求解器的基类，包含了求解器的抽象功能。它定义了数据流分析的基本流程和步骤，沿着CFG来对数据进行分析处理，它的具体分析逻辑和迭代算法由它的子类实现提供。
- `pascal.taie.analysis.dataflow.solver.IterativeSolver`
  - 这个类扩展了 `Solver` 的功能并实现了迭代求解算法。

## ir

- `pascal.taie.ir.exp.Exp`
  - 这是 Tai-e 的 IR 中的一个关键接口，用于表示程序中中间语言(IR)的所有表达式。
- `pascal.taie.ir.stmt.Stmt`
  - 这是 Tai-e 的 IR 中的另一个关键接口，它用于表示程序中的所有语句。对于一个典型的程序设计语言来说，**每个表达式都属于某条特定的语句**。这里statement泛指所有的语句，其中至多只可能定义一个变量、而可能使用零或多个变量。
- 这里注意区分表达式和语句的区别
  - 表达式(exp)通常用来计算值，它们可能由常量、变量、操作符（如加减乘除）以及函数调用组合而成。比如算数表达式 `a + b`, 方法调用 `foo()`
  - 语句(stmt)是执行的单位，它们执行操作可能会改变程序的状态。包括赋值，控制流决策，循环等，比如赋值语句 `x = a + b` 包含了赋值操作，和算术表达式 `a + b`
  - 因此，表达式是构成语句的元素，而语句则用表达式来执行程序中的操作。在数据流分析中，理解一个语句如何使用和定义表达式是至关重要的，因为它影响了变量在程序执行过程中的生命周期和作用域。

## 流程理解

- 对于整个流程，就是使用 `DataflowAnalysis` 与 `Solver` 进行交互。
  - `DataflowAnalysis` 对数据流进行处理, 使用子类 LiveVariableAnalysis 对基类的功能进行扩展, 实现了分析流中的初始化, Transfer Function 与 Control Flow 的实现。
  - `Solver` 实现了迭代解释器, 它通过子类 lterativeSolver 对基类功能进行实现。之后通过 cfg 表示当前的控制流, 然后使用由 DataflowAnalysis 定义的 analysis 来对 cfg 进行操作。
    - 这里 `cfg` 是对于 Node 进行各项操作, 比如判断是否 Exit, 遍历寻找所有 BB。
    - 而 `analysis` 则是包含了所有对于数据流的操作，比如 meetInto, transferNode。 

> [参考代码](https://github.com/pascal-lab/Tai-e-assignments/commit/2b21a6dd84d81a56cdffb0c91b575d655d5b1da0)

# 疑问

1. 在 `Assign.java` 中，soot(Tai-e)转化为三地址码时，对于语句

   ```java
   int d = a + b;
   c = a;
   // 这里转化为三地址码，增加了临时变量temp$0
   /*
   	temp$0 = a;
       d = temp$0 + b;
       b = d;
       c = a;
   */
   ```

   - 在询问chatgpt时，它给出的答案是这里引入`temp$0`并不是为了防止`a`被修改，而是为了确保在计算`d`的过程中，`a`的原始值保持不变。这可能是为了在计算过程中确保`a`的值不会被无意间或意外地修改，以便获得正确的结果。
   - 这里我感觉它说的有道理，为了防止潜在的对a进行更改的可能而引入临时变量进行存储(~~感觉是囿于静态分析sound-->may analysis的性质，可能更改我就提出阻止策略~~, 但是又感觉不对，静态分析是在IR基础上进行的，这里是生成IR的过程)
