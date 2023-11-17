/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis;

import fj.P;
import pascal.taie.analysis.MethodAnalysis;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.cfg.Edge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.ArithmeticExp;
import pascal.taie.ir.exp.ArrayAccess;
import pascal.taie.ir.exp.CastExp;
import pascal.taie.ir.exp.FieldAccess;
import pascal.taie.ir.exp.NewExp;
import pascal.taie.ir.exp.RValue;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.AssignStmt;
import pascal.taie.ir.stmt.If;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.SwitchStmt;
import pascal.taie.util.collection.Pair;

import java.util.*;
import java.util.concurrent.locks.Condition;

public class DeadCodeDetection extends MethodAnalysis {

    public static final String ID = "deadcode";

    public DeadCodeDetection(AnalysisConfig config) {
        super(config);
    }

    @Override
    public Set<Stmt> analyze(IR ir) {
        // obtain CFG
        CFG<Stmt> cfg = ir.getResult(CFGBuilder.ID);
        // obtain result of constant propagation
        DataflowResult<Stmt, CPFact> constants =
                ir.getResult(ConstantPropagation.ID);
        // obtain result of live variable analysis
        DataflowResult<Stmt, SetFact<Var>> liveVars =
                ir.getResult(LiveVariableAnalysis.ID);
        // keep statements (dead code) sorted in the resulting set
        Set<Stmt> deadCode = new TreeSet<>(Comparator.comparing(Stmt::getIndex));
        // TODO - finish me
        // Your task is to recognize dead code in ir and add it to deadCode、
        Queue<Stmt> stmtQueue = new LinkedList<>();
        Set<Stmt> visited = new HashSet<>();

        // 将入口节点添加到队列和已访问集合中
        Stmt entry = cfg.getEntry();
        stmtQueue.add(cfg.getEntry());
        visited.add(entry);
        // cfg出发，提出了所有的不可达语句
        while (!stmtQueue.isEmpty()) {
            Stmt current = stmtQueue.poll();
            // if 分支不可达
            if (current instanceof If s) {
                // 获取 if 中表达式的结果，evaluate用来处理当前表达式与输入值的返回结果
                Value ConditionValue = ConstantPropagation.evaluate(s.getCondition(), constants.getInFact(current));
                if (ConditionValue.isConstant()) {
                    // 如果是常量，判断是否有可达分支
                    for (Edge<Stmt> edge : cfg.getOutEdgesOf(s)) {
                        // 判断 if 可达语句
                        if ((ConditionValue.getConstant() == 1 && edge.getKind() == Edge.Kind.IF_TRUE) ||
                                ConditionValue.getConstant() == 0 && edge.getKind() == Edge.Kind.IF_FALSE) {
                            // 满足if，跳转到目标语句，同时目标语句设置已被访问
                            current = edge.getTarget();
                            visited.add(current);
                        }
                    }
                }
            }
            // switch 分支不可达
            if (current instanceof SwitchStmt s) {
                Value ConditionValue = ConstantPropagation.evaluate(s.getVar(), constants.getInFact(current));
                if(ConditionValue.isConstant()){
                    // 判断是否有可达分支
                    boolean flag = false;
                    // 获取所有的 case
                    for (Pair<Integer, Stmt> pair : s.getCaseTargets()){
                        if (ConditionValue.getConstant() == pair.first()) {
                            flag = true;
                            current = pair.second();
                            visited.add(current);
                        }
                    }
                    // 如果没有可达分支，将 default 加入队列
                    if (!flag) {
                        current = s.getDefaultTarget();
                        visited.add(current);
                    }
                }
            }

            // 无用赋值
            if(current instanceof AssignStmt<?,?> s && s.getLValue() instanceof Var var){
                // 如果当前变量不在活跃变量中，且右值没有副作用，这个时候才能进行删除
                if (!liveVars.getResult(s).contains(var) && hasNoSideEffect(s.getRValue())) {
                    visited.remove(s);
                }
            }
            // 将所有未访问的后继节点添加到队列中
            for (Stmt succ : cfg.getSuccsOf(current)) {
                if (!visited.contains(succ)) {
                    stmtQueue.add(succ);
                    visited.add(succ);
                }
            }
        }
        // 去除所有可达语句，剩下的就是不可达语句，注意去除出口节点
        deadCode.addAll(cfg.getNodes());
        deadCode.removeAll(visited);
        deadCode.remove(cfg.getExit());
        return deadCode;
    }

    /**
     * @return true if given RValue has no side effect, otherwise false.
     */
    private static boolean hasNoSideEffect(RValue rvalue) {
        // new expression modifies the heap
        if (rvalue instanceof NewExp ||
                // cast may trigger ClassCastException
                rvalue instanceof CastExp ||
                // static field access may trigger class initialization
                // instance field access may trigger NPE
                rvalue instanceof FieldAccess ||
                // array access may trigger NPE
                rvalue instanceof ArrayAccess) {
            return false;
        }
        if (rvalue instanceof ArithmeticExp) {
            ArithmeticExp.Op op = ((ArithmeticExp) rvalue).getOperator();
            // may trigger DivideByZeroException
            return op != ArithmeticExp.Op.DIV && op != ArithmeticExp.Op.REM;
        }
        return true;
    }
}
