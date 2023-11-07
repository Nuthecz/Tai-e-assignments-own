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

import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.LValue;
import pascal.taie.ir.exp.RValue;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;

/**
 * Implementation of classic live variable analysis.
 */
public class LiveVariableAnalysis extends
        AbstractDataflowAnalysis<Stmt, SetFact<Var>> {

    public static final String ID = "livevar";

    public LiveVariableAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return false;
    }

    @Override
    public SetFact<Var> newBoundaryFact(CFG<Stmt> cfg) {
        /* TODO - finish me */
        //返回边界节点的向量，backward 的边界节点是 IN[exit] = 空
        return new SetFact<>();
    }

    @Override
    public SetFact<Var> newInitialFact() {
        /* TODO - finish me */
        // 返回初始化节点的向量，backward 的IN[B]=空
        return new SetFact<>();
    }

    @Override
    public void meetInto(SetFact<Var> fact, SetFact<Var> target) {
        /* TODO - finish me */
        // target是OUT,将facts结合到target上
        target.union(fact);
    }

    @Override
    public boolean transferNode(Stmt stmt, SetFact<Var> in, SetFact<Var> out) {
        /* TODO - finish me */
        //这里设置中间值Tmp,以便之后进行对比查看IN是否发生变化
        SetFact<Var> NewInTmp = new SetFact<>();
        //对Tmp进行赋值
        NewInTmp.union(out);
        //获取defB,之后获取数据,判断类型是否相同,然后删除
        if(stmt.getDef().isPresent()){
            LValue def = stmt.getDef().get();
            if(def instanceof Var){
                NewInTmp.remove((Var)def);
            }
        }
        //遍历useB,之后使用add进行添加(这里union类型为SetFact<Var>,所以不能使用)
        for(RValue Use: stmt.getUses()){
            if(Use instanceof Var){
                NewInTmp.add((Var) Use);
            }
        }
        //比对transfer function之后的结果和一开始的结果有没有发生变化,不同就更新(set)
        if(!NewInTmp.equals(in)){
            in.set(NewInTmp);
            return true;
        }
        return false;
    }
}
