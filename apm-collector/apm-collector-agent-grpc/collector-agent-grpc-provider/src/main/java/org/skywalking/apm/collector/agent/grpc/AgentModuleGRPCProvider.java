/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.grpc;

import java.util.Properties;
import org.skywalking.apm.collector.agent.grpc.handler.ApplicationRegisterServiceHandler;
import org.skywalking.apm.collector.agent.grpc.handler.InstanceDiscoveryServiceHandler;
import org.skywalking.apm.collector.agent.grpc.handler.JVMMetricsServiceHandler;
import org.skywalking.apm.collector.agent.grpc.handler.ServiceNameDiscoveryServiceHandler;
import org.skywalking.apm.collector.agent.grpc.handler.TraceSegmentServiceHandler;
import org.skywalking.apm.collector.agent.grpc.handler.naming.AgentGRPCNamingHandler;
import org.skywalking.apm.collector.agent.grpc.handler.naming.AgentGRPCNamingListener;
import org.skywalking.apm.collector.agent.stream.AgentStreamModule;
import org.skywalking.apm.collector.cluster.ClusterModule;
import org.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.grpc.manager.GRPCManagerModule;
import org.skywalking.apm.collector.grpc.manager.service.GRPCManagerService;
import org.skywalking.apm.collector.naming.NamingModule;
import org.skywalking.apm.collector.naming.service.NamingHandlerRegisterService;
import org.skywalking.apm.collector.server.Server;

/**
 * @author peng-yongsheng
 */
public class AgentModuleGRPCProvider extends ModuleProvider {

    public static final String NAME = "gRPC";
    private static final String HOST = "host";
    private static final String PORT = "port";

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return AgentGRPCModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {

    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        String host = config.getProperty(HOST);
        Integer port = (Integer)config.get(PORT);

        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(AgentGRPCModule.NAME, this.name(), new AgentModuleGRPCRegistration(host, port));

        AgentGRPCNamingListener namingListener = new AgentGRPCNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        NamingHandlerRegisterService namingHandlerRegisterService = getManager().find(NamingModule.NAME).getService(NamingHandlerRegisterService.class);
        namingHandlerRegisterService.register(new AgentGRPCNamingHandler(namingListener));

        GRPCManagerService managerService = getManager().find(GRPCManagerModule.NAME).getService(GRPCManagerService.class);
        Server gRPCServer = managerService.createIfAbsent(host, port);

        addHandlers(gRPCServer);
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, NamingModule.NAME, GRPCManagerModule.NAME, AgentStreamModule.NAME};
    }

    private void addHandlers(Server gRPCServer) {
        gRPCServer.addHandler(new ApplicationRegisterServiceHandler(getManager()));
        gRPCServer.addHandler(new InstanceDiscoveryServiceHandler(getManager()));
        gRPCServer.addHandler(new ServiceNameDiscoveryServiceHandler(getManager()));
        gRPCServer.addHandler(new JVMMetricsServiceHandler(getManager()));
        gRPCServer.addHandler(new TraceSegmentServiceHandler(getManager()));
    }
}
