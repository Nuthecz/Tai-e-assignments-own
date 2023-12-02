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

package pascal.taie.analysis.graph.callgraph;

import pascal.taie.World;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.Subsignature;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Implementation of the CHA algorithm.
 */
class CHABuilder implements CGBuilder<Invoke, JMethod> {

    private ClassHierarchy hierarchy;

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        return buildCallGraph(World.get().getMainMethod());
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        DefaultCallGraph callGraph = new DefaultCallGraph();
        callGraph.addEntryMethod(entry);
        /* TODO - finish me */
        Queue<JMethod> WorkList = new ArrayDeque<>();
        WorkList.add(entry);
        while(!WorkList.isEmpty()){
            JMethod method = WorkList.poll();
            // 添加为可达方法
            if(!callGraph.reachableMethods.contains(method))
                callGraph.addReachableMethod(method);
            // 遍历该方法的调用语句(通过方法中调用点来获取)，将调用的方法加入到队列中
            for(Invoke callSite: callGraph.getCallSitesIn(method)){
                Set<JMethod> possibleCallees = resolve(callSite);
                for(JMethod callee : possibleCallees){
                    callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(callSite), callSite, callee));
                    WorkList.add(callee);
                }
            }
        }
        return callGraph;
    }

    /**
     * Resolves call targets (callees) of a call site via CHA.
     */
    private Set<JMethod> resolve(Invoke callSite) {
        /* TODO - finish me */
        // 维护结果集，就是Resolve中的 T
        Set<JMethod> possibleTarGet = new HashSet<>();
        // methodRef包含了调用点所调用的目标方法的签名信息
        MethodRef methodRef = callSite.getMethodRef();
        switch (CallGraphs.getCallKind(callSite)){
            case STATIC:
            case SPECIAL:// 调用静态方法或者私有方法，直接dispathc寻找对应的方法
                JMethod method = dispatch(methodRef.getDeclaringClass(),methodRef.getSubsignature());
                // 首先判定是否找到了对应的方法，如果找到了就将其加入到结果集中
                if(method != null)
                    possibleTarGet.add(method);
                break;
            case VIRTUAL:
            case INTERFACE:// 调用虚方法或者接口方法，需要根据继承关系进行查找
                Queue<JClass> jClassQueue = new ArrayDeque<>();
                jClassQueue.add(methodRef.getDeclaringClass());
                while(!jClassQueue.isEmpty()){
                    JClass jClass = jClassQueue.poll();
                    method = dispatch(jClass, methodRef.getSubsignature());
                    // 如果找到了对应的方法，就将其加入到结果集中
                    if(method != null)
                        possibleTarGet.add(method);
                    // 将该类(jClass)的所有继承关系加入到队列中--->不能保证jClass是一个类，而不是接口
                    jClassQueue.addAll(hierarchy.getDirectSubclassesOf(jClass));
                    jClassQueue.addAll(hierarchy.getDirectSubinterfacesOf(jClass));
                    jClassQueue.addAll(hierarchy.getDirectImplementorsOf(jClass));
                }
                break;
        }
        return possibleTarGet;
    }

    /**
     * Looks up the target method based on given class and method subsignature.
     *
     * @return the dispatched target method, or null if no satisfying method
     * can be found.
     */
    private JMethod dispatch(JClass jclass, Subsignature subsignature) {
        /* TODO - finish me */
        // 根据子签名返回该类中声明的对应方法，这是最后的递归出口
        JMethod method =  jclass.getDeclaredMethod(subsignature);
        if(method != null) return method;

        // 根据签名递归到父类中查找对应方法
        JClass jClass = jclass.getSuperClass();
        if(jClass != null) return dispatch(jClass, subsignature);

        // 其余情况返回null
        return null;
    }
}
