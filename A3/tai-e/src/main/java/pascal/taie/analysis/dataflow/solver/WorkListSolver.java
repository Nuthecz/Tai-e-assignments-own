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

import fj.P;
import pascal.taie.analysis.dataflow.analysis.DataflowAnalysis;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.cfg.CFG;

import java.util.ArrayDeque;
import java.util.Queue;

class WorkListSolver<Node, Fact> extends Solver<Node, Fact> {

    WorkListSolver(DataflowAnalysis<Node, Fact> analysis) {
        super(analysis);
    }

    @Override
    protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        /* TODO - finish me */
        Queue<Node> WorkList = new ArrayDeque<>(cfg.getNodes());
        while(!WorkList.isEmpty()){
            Node BasicNode = WorkList.poll();
            if(cfg.isEntry(BasicNode)) continue;
            Fact in  = result.getInFact(BasicNode);
            Fact out = result.getOutFact(BasicNode);

            for(Node PreNode: cfg.getPredsOf(BasicNode)){
                analysis.meetInto(result.getOutFact(PreNode), in);
            }

            if(!analysis.transferNode(BasicNode, in, out)){
                for(Node SucNode: cfg.getSuccsOf(BasicNode)){
                    if(!WorkList.contains(SucNode)){
                        WorkList.add(SucNode);
                    }
                }
            }
        }
    }

    @Override
    protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        /* TODO - finish me */
        Queue<Node> WorkList = new ArrayDeque<>(cfg.getNodes());
        while(!WorkList.isEmpty()){
            Node BasicNode = WorkList.poll();
            if(cfg.isExit(BasicNode)) continue;
            Fact in  = result.getInFact(BasicNode);
            Fact out = result.getOutFact(BasicNode);

            for(Node SucNode: cfg.getSuccsOf(BasicNode)){
                analysis.meetInto(result.getInFact(SucNode), out);
            }

            if(analysis.transferNode(BasicNode, in, out)){
                for(Node PreBlock: cfg.getPredsOf(BasicNode)){
                    if(!WorkList.contains(PreBlock)){
                        WorkList.add(PreBlock);
                    }
                }
            }
        }
    }
}
