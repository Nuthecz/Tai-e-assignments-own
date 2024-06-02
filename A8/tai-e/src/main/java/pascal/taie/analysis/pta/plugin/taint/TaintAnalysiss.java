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

package pascal.taie.analysis.pta.plugin.taint;

import com.fasterxml.jackson.core.filter.JsonPointerBasedFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.*;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.cs.Solver;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

import java.util.*;

import pascal.taie.language.type.Type;

class TaintFlowGraph {

    /**
     * Map from a Taint (node) to its successors in TFG.
     */
    private final MultiMap<Pointer, Pointer> successors = Maps.newMultiMap();

    /**
     * Adds an edge (source -> target) to this TFG.
     *
     * @return true if this TFG changed as a result of the call,
     * otherwise false.
     */
    boolean addEdge(Pointer source, Pointer target) {
        return successors.put(source, target);
    }

    /**
     * @return successors of given pointer in the TFG.
     */
    Set<Pointer> getSuccsOf(Pointer pointer) {
        return successors.get(pointer);
    }
}

class SinkCallSite {
    CSCallSite callSite;

    Sink sink;

    SinkCallSite(CSCallSite callSite, Sink sink) {
        this.callSite = callSite;
        this.sink = sink;
    }
}

public class TaintAnalysiss {

    private static final Logger logger = LogManager.getLogger(TaintAnalysiss.class);

    private final TaintManager manager;

    private final TaintConfig config;

    private final Solver solver;

    private final CSManager csManager;

    private final Context emptyContext;

    private List<SinkCallSite> sinkCallSites;

    private TaintFlowGraph taintFlowGraph;

    public TaintAnalysiss(Solver solver) {
        manager = new TaintManager();
        this.solver = solver;
        csManager = solver.getCSManager();
        emptyContext = solver.getContextSelector().getEmptyContext();
        config = TaintConfig.readConfig(
                solver.getOptions().getString("taint-config"),
                World.get().getClassHierarchy(),
                World.get().getTypeSystem());
        logger.info(config);

        sinkCallSites = new ArrayList<>();
        taintFlowGraph = new TaintFlowGraph();
    }

    // TODO - finish me
    // 处理 Source，在 Invoke 中判断是否为 Source，是则添加 taint object
    public PointsToSet dealSource(Invoke callSite, JMethod jMethod) {
        for (Source source : config.getSources()) {
            // 比对调用方法和存储的 Source 是否有一样的，一样则产生 taint object，此时的 callSite 就是 Source
            if (source.method().getSignature().equals(jMethod.getSignature())) {
                // 创建 taint object，使用空上下文作为污点对象t(source, type)的堆上下文
                return PointsToSetFactory.make(
                        csManager.getCSObj(emptyContext, manager.makeTaint(callSite, jMethod.getReturnType())));
            }
        }
        return null;
    }

    // 处理 Sink，在遇见 sink 点时添加 sink 到 sinkCallSites
    // 从而可以保存所有的 csCallSite，然后在 collectTaintFlows 中进行处理形成 taintFlows
    public void dealSinkCallSite(CSCallSite callSite, JMethod jMethod) {
        config.getSinks().forEach(sink -> {
            if (sink.method().getSignature().equals(jMethod.getSignature())) {
                sinkCallSites.add(new SinkCallSite(callSite, sink));
            }
        });
    }

    // 仿照 PFG 的 propagate 将 taint object 向后继节点进行传播
    // 这里就是判断当 delta 中包含污点对象时，应该基于 "污点传播边" 向后继节点传播污点对象
    public void propagate(Pointer pointer, PointsToSet delta) {
        PointsToSet taint = PointsToSetFactory.make();
        // 获取 delta 中的 taint object
        delta.forEach(csObj -> {
            if (manager.isTaint(csObj.getObject())) {
                taint.addObject(csObj);
            }
        });
        if (taint.isEmpty()) return;
        // 基于这些 "污点传播边" 向后继节点传播 taint object，即在 WorkList 中添加 <t, pt(s)>
        taintFlowGraph.getSuccsOf(pointer).forEach(suc -> {
            solver.addWorkList(suc, taint);
        });
    }

    // 在 TFG 中添加边 "source -> target"，同时调用 workList.addEntry，添加 target 指向 pts(source)
    private void addTFGEdge(Pointer source, Pointer target, Type type) {
        // 检查污点传播的边是否存在。同时若是静态调用，则 source 和 target 其中一个为 null，直接进行下面的 if 判断返回
        // 符合静态方法没有 base 变量，所以他们不会引起 base-to-result 和 arg-to-base 的污点传播的情况
        // 这里 addEdge 返回 boolean，存在边返回 true， 这里不存在就进行 添加边的处理，顺便区分了静态调用和动态调用
        if (!taintFlowGraph.addEdge(source, target)) return;
        source.getPointsToSet().forEach(csObj -> {
            Obj obj = csObj.getObject();
            if (manager.isTaint(obj)) {
                solver.addWorkList(target, PointsToSetFactory.make(
                        csManager.getCSObj(emptyContext, manager.makeTaint(manager.getSourceCall(obj), type))));
            }
        });
    }

    public void dealTaintTransfer(CSCallSite csCallSite, JMethod jMethod, CSVar csVar) {
        Context context = csCallSite.getContext();
        config.getTransfers().forEach(taintTransfer -> {
            if (taintTransfer.method().getSignature().equals(jMethod.getSignature())) {
                // 这里 taintTransfer.from() 指的是相应 taint object 参数的下标，使用 index 替代
                int index = taintTransfer.from();
                if (index == TaintTransfer.BASE && taintTransfer.to() == TaintTransfer.RESULT) {
                    // base-to-result，这里 csVar 是 recv，也就是 base变量
                    Var lVar = csCallSite.getCallSite().getLValue();
                    addTFGEdge(csVar, csManager.getCSVar(context, lVar), jMethod.getReturnType());
                } else if (index >= 0 && taintTransfer.to() == TaintTransfer.BASE) {
                    // Arg-to-base，根据 index 找到 args 中对应的 taint object
                    List<Var> args = csCallSite.getCallSite().getInvokeExp().getArgs();
                    CSVar arg = csManager.getCSVar(context, args.get(index));
                    addTFGEdge(arg, csVar, jMethod.getReturnType());
                } else if (index >= 0 && taintTransfer.to() == TaintTransfer.RESULT) {
                    // Arg-to-result
                    List<Var> args = csCallSite.getCallSite().getInvokeExp().getArgs();
                    CSVar arg = csManager.getCSVar(context, args.get(index));
                    Var lVar = csCallSite.getCallSite().getLValue();
                    addTFGEdge(arg, csManager.getCSVar(context, lVar), jMethod.getReturnType());
                }
            }
        });
    }

    public void onFinish() {
        Set<TaintFlow> taintFlows = collectTaintFlows();
        solver.getResult().storeResult(getClass().getName(), taintFlows);
    }

    private Set<TaintFlow> collectTaintFlows() {
        Set<TaintFlow> taintFlows = new TreeSet<>();
        PointerAnalysisResult result = solver.getResult();
        // TODO - finish me
        // You could query pointer analysis results you need via variable result.
        // 遍历保存的所有 csCallSite 点，形成 <j, l, i> ⊏ TaintFlows
        sinkCallSites.forEach(sinkCallSite -> {
            CSCallSite csCallSite = sinkCallSite.callSite;
            Sink sink = sinkCallSite.sink;
            // 获取调用点的参数的下标 index
            List<Var> args = csCallSite.getCallSite().getInvokeExp().getArgs();
            // 遍历参数 a_i 的所有指针集，若是包含 taint object，则增加 <j, l, i> 到 TaintFlows 中
            result.getPointsToSet(csManager.getCSVar(
                    csCallSite.getContext(), args.get(sink.index()))).forEach(csObj -> {
                if (manager.isTaint(csObj.getObject())) {
                    taintFlows.add(new TaintFlow(
                            manager.getSourceCall(csObj.getObject()), csCallSite.getCallSite(), sink.index()));
                }
            });
        });
        return taintFlows;
    }
}
