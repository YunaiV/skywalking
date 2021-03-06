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

package org.skywalking.apm.collector.ui.jetty.handler.instancehealth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.service.InstanceHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * 获得应用的应用实例健康相关信息数组
 *
 * @author peng-yongsheng
 */
public class InstanceHealthGetHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthGetHandler.class);

    @Override public String pathSpec() {
        return "/instance/health/applicationId";
    }

    private final InstanceHealthService service;

    public InstanceHealthGetHandler(ModuleManager moduleManager) {
        this.service = new InstanceHealthService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timeBucketStr = req.getParameter("timeBucket");
        String[] applicationIdsStr = req.getParameterValues("applicationIds");
        logger.debug("instance health get timeBucket: {}, applicationIdsStr: {}", timeBucketStr, applicationIdsStr);

        // 解析 timeBucket
        long timeBucket;
        try {
            timeBucket = Long.parseLong(timeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timestamp must be long");
        }

        // 解析 应用编号数组
        int[] applicationIds = new int[applicationIdsStr.length];
        for (int i = 0; i < applicationIdsStr.length; i++) {
            try {
                applicationIds[i] = Integer.parseInt(applicationIdsStr[i]);
            } catch (NumberFormatException e) {
                throw new ArgumentsParseException("application id must be integer");
            }
        }

        // 返回字段设置
        JsonObject response = new JsonObject();
        response.addProperty("timeBucket", timeBucket);
        JsonArray appInstances = new JsonArray();
        response.add("appInstances", appInstances);

        // 循环应用编号数组
        for (int applicationId : applicationIds) {
            // 以应用编号为聚合，获得应用实例数组
            appInstances.add(service.getInstances(timeBucket, applicationId));
        }
        return response;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
