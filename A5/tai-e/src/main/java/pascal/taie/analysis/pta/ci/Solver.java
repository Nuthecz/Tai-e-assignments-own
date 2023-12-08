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

package pascal.taie.analysis.pta.ci;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final HeapModel heapModel;

    private DefaultCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private StmtProcessor stmtProcessor;

    private ClassHierarchy hierarchy;

    Solver(HeapModel heapModel) {
        this.heapModel = heapModel;
    }

    /**
     * Runs pointer analysis algorithm.
     */
    void solve() {
        initialize();
        analyze();
    }

    /**
     * Initializes pointer analysis.
     */
    private void initialize() {
        workList = new WorkList();
        pointerFlowGraph = new PointerFlowGraph();
        callGraph = new DefaultCallGraph();
        stmtProcessor = new StmtProcessor();
        hierarchy = World.get().getClassHierarchy();
        // initialize main method
        JMethod main = World.get().getMainMethod();
        callGraph.addEntryMethod(main);
        addReachable(main);
    }

    /**
     * Processes new reachable method.
     */
    private void addReachable(JMethod method) {
        /* TODO - finish me */
        if (!callGraph.addReachableMethod(method)) return;
        method.getIR().forEach(stmt -> {
            stmt.accept(stmtProcessor);
        });

    }

    /**
     * Processes statements in new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {
        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        // 1. New x = new T()
        @Override
        public Void visit(New stmt){
            Pointer pointer = pointerFlowGraph.getVarPtr(stmt.getLValue());
            // Allocation-Site Abstraction
            PointsToSet pointsToSet = new PointsToSet(heapModel.getObj(stmt));
            workList.addEntry(pointer, pointsToSet);
            return null;
        }
        // 2. Copy x = y
        @Override
        public Void visit(Copy stmt){
            Pointer source = pointerFlowGraph.getVarPtr(stmt.getRValue());
            Pointer target = pointerFlowGraph.getVarPtr(stmt.getLValue());
            addPFGEdge(source, target);
            return StmtVisitor.super.visit(stmt);
        }

        // 3. x = y.f
        @Override
        public Void visit(LoadField stmt){
            if (!stmt.isStatic()) return null;
            JField f = stmt.getFieldRef().resolve();
            Var x = stmt.getLValue();
            addPFGEdge(pointerFlowGraph.getStaticField(f), pointerFlowGraph.getVarPtr(x));
            return null;
        }
        // 4. x.f = y
        @Override
        public Void visit(StoreField stmt){
            if(!stmt.isStatic()) return null; // static也不是必要的，AddReachable和analysis都可以使用
            JField f = stmt.getFieldRef().resolve();
            Var y = stmt.getRValue();
            addPFGEdge(pointerFlowGraph.getVarPtr(y), pointerFlowGraph.getStaticField(f));
            return null;
        }
        // 5. y = x.m(...) 静态方法处理
        @Override
        public Void visit(Invoke stmt){
            if (stmt.isStatic()) { // 与方法调用相比，少了dispatch 和 返回值
                MethodRef methodRef = stmt.getMethodRef();
                JMethod m = methodRef.getDeclaringClass().getDeclaredMethod(methodRef.getSubsignature());
                processEachCall(stmt, m);
            }
            return null;
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        /* TODO - finish me */
        if (pointerFlowGraph.addEdge(source, target)) {
            workList.addEntry(target, source.getPointsToSet());
        }
    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        /* TODO - finish me */
        while (!workList.isEmpty()){
            // 1. remove <n,pts> from WL
            WorkList.Entry entry = workList.pollEntry();
            Pointer ptr = entry.pointer();
            PointsToSet pts = entry.pointsToSet();
            // 2. get the Δ
            PointsToSet delta = propagate(ptr, pts);
            // 3. if n represents a variable x then
            if (ptr instanceof VarPtr varPtr) {
                // 3.1 foreach oi in Δ do
                Var x = varPtr.getVar();
                if (delta.isEmpty()) continue;
                delta.forEach(obj -> {
                    // 3.1.1 foreach y = x.f in S do
                    x.getLoadFields().forEach(loadField -> {
                        JField f = loadField.getFieldRef().resolve();
                        Var y = loadField.getLValue();
                        addPFGEdge(pointerFlowGraph.getInstanceField(obj, f), pointerFlowGraph.getVarPtr(y));
                    });
                    // 3.1.2 foreach x.f = y in S do
                    x.getStoreFields().forEach(storeField -> {
                        JField f = storeField.getFieldRef().resolve();
                        Var y = storeField.getRValue();
                        addPFGEdge(pointerFlowGraph.getVarPtr(y), pointerFlowGraph.getInstanceField(obj, f));
                    });
                    // 3.1.3 foreach y = x[*] in S do
                    x.getLoadArrays().forEach(loadArray -> {
                        Var y = loadArray.getLValue();
                        addPFGEdge(pointerFlowGraph.getArrayIndex(obj), pointerFlowGraph.getVarPtr(y));
                    });
                    // 3.1.4 foreach x[*] = y in S do
                    x.getStoreArrays().forEach(storeArray -> {
                        Var y = storeArray.getRValue();
                        addPFGEdge(pointerFlowGraph.getVarPtr(y), pointerFlowGraph.getArrayIndex(obj));
                    });
                    // 3.1.5 foreach y = x.m(...) in S do 方法调用
                    processCall(x, obj);
                });
            }
        }
    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        /* TODO - finish me */
        // 1. pts is not empty
        PointsToSet delta = new PointsToSet();
        if (pointsToSet.isEmpty()) return delta;
        // 2. Δ = pts - pt(n) and pt(n) U= pts
        pointsToSet.forEach(obj -> {
            if (pointer.getPointsToSet().addObject(obj)){
                delta.addObject(obj);
                pointer.getPointsToSet().addObject(obj);
            }
        });
        // 3. foreach m in succ(n) do
        if (delta.isEmpty()) return delta;
        pointerFlowGraph.getSuccsOf(pointer).forEach(suc -> {
            workList.addEntry(suc, delta);
        });
        return delta;
    }

    private void processEachCall(Invoke x, JMethod m){
        // 1. if l -> m is not in CG then
        if (callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(x), x, m))){
            // 2. add reachable(m)
            addReachable(m);
            // 3. foreach ai = xi ∈ S do
            for (int i = 0; i < x.getInvokeExp().getArgCount(); i ++) {
                Var arg = x.getInvokeExp().getArg(i);
                Var param = m.getIR().getParam(i);
                addPFGEdge(pointerFlowGraph.getVarPtr(arg), pointerFlowGraph.getVarPtr(param));
            }
            // 4. add the edge (x, m_ret) to PFG
            if (x.getLValue() != null) { // judge the rev is not exist
                m.getIR().getReturnVars().forEach(m_ret -> {
                    addPFGEdge(pointerFlowGraph.getVarPtr(m_ret), pointerFlowGraph.getVarPtr(x.getLValue()));
                });
            }
        }
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param var the variable that holds receiver objects
     * @param recv a new discovered object pointed by the variable.
     */
    private void processCall(Var var, Obj recv) {
        // TODO - finish me
        // 1. foreach l: r = x.k(a1,…,an) ∈ S do
        var.getInvokes().forEach(x -> {
            // 2. m = Dispatch(o_i, k)
            JMethod m = resolveCallee(recv, x);
            Var m_this = m.getIR().getThis();
            // 3. add <m_this, {o_i}> to WL
            workList.addEntry(pointerFlowGraph.getVarPtr(m_this), new PointsToSet(recv));
            // process the call
            processEachCall(x, m);
        });
    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv     the receiver object of the method call. If the callSite
     *                 is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(Obj recv, Invoke callSite) {
        Type type = recv != null ? recv.getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    CIPTAResult getResult() {
        return new CIPTAResult(pointerFlowGraph, callGraph);
    }
}
