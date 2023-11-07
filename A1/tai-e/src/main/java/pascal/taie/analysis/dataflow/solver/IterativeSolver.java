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

package pascal.taie.analysis.dataflow.solver;

import pascal.taie.analysis.dataflow.analysis.DataflowAnalysis;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.cfg.CFG;

class IterativeSolver<Node, Fact> extends Solver<Node, Fact> {

    public IterativeSolver(DataflowAnalysis<Node, Fact> analysis) {
        super(analysis);
    }

    @Override
    protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        /* TODO - finish me */
        // 设置中止循环: 到达不动点
        boolean flag = true;
        while(flag){
            flag = false;
            // 循环遍历所有的 Basic Block
            for(Node BasicBlock: cfg.getNodes()){
                // 跳过EXIT BB
                if(cfg.isExit(BasicBlock)) continue;
                // 获得当前 BB 的所有前驱 BB, 之后进行 Union
                for(Node SuccBlock: cfg.getSuccsOf(BasicBlock)){
                    analysis.meetInto(result.getInFact(SuccBlock), result.getOutFact(BasicBlock));
                }
                // Transfer Function的实现, 同时完成是否达到不动点的判断
                if(analysis.transferNode(BasicBlock,result.getInFact(BasicBlock),result.getOutFact(BasicBlock))){
                    flag = true;
                }
            }
        }
    }
}
