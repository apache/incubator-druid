/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.testing.clients;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.java.util.http.client.HttpClient;
import org.apache.druid.java.util.http.client.Request;
import org.apache.druid.java.util.http.client.response.StatusResponseHandler;
import org.apache.druid.java.util.http.client.response.StatusResponseHolder;
import org.apache.druid.testing.IntegrationTestingConfig;
import org.apache.druid.testing.guice.TestClient;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public abstract class AbstractQueryResourceTestClient<QueryType>
{
  private static final Logger LOG = new Logger(AbstractQueryResourceTestClient.class);
  private static final StatusResponseHandler RESPONSE_HANDLER = new StatusResponseHandler(StandardCharsets.UTF_8);

  private final ObjectMapper jsonMapper;
  private final HttpClient httpClient;
  final String routerUrl;

  @Inject
  AbstractQueryResourceTestClient(
      ObjectMapper jsonMapper,
      @TestClient HttpClient httpClient,
      IntegrationTestingConfig config
  )
  {
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    this.routerUrl = config.getRouterUrl();
  }

  public abstract String getBrokerURL();

  public List<Map<String, Object>> query(String url, QueryType query)
  {
    try {
      StatusResponseHolder response = httpClient.go(
          new Request(HttpMethod.POST, new URL(url)).setContent(
              "application/json",
              jsonMapper.writeValueAsBytes(query)
          ), RESPONSE_HANDLER

      ).get();

      if (!response.getStatus().equals(HttpResponseStatus.OK)) {
        throw new ISE(
            "Error while querying[%s] status[%s] content[%s]",
            getBrokerURL(),
            response.getStatus(),
            response.getContent()
        );
      }

      return jsonMapper.readValue(
          response.getContent(), new TypeReference<List<Map<String, Object>>>()
          {
          }
      );
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static StatusResponseHolder makeRequest(HttpClient httpClient, HttpMethod method, String url, byte[] content)
  {
    return makeRequestWithExpectedStatus(
        httpClient,
        method,
        url,
        content,
        HttpResponseStatus.OK
    );
  }

  public static StatusResponseHolder makeRequestWithExpectedStatus(
      HttpClient httpClient,
      HttpMethod method,
      String url,
      @Nullable byte[] content,
      HttpResponseStatus expectedStatus
  )
  {
    try {
      Request request = new Request(method, new URL(url));
      if (content != null) {
        request.setContent(MediaType.APPLICATION_JSON, content);
      }
      int retryCount = 0;

      StatusResponseHolder response;

      while (true) {
        response = httpClient.go(request, RESPONSE_HANDLER).get();

        if (!response.getStatus().equals(expectedStatus)) {
          String errMsg = StringUtils.format(
              "Error while making request to url[%s] status[%s] content[%s]",
              url,
              response.getStatus(),
              response.getContent()
          );
          // it can take time for the auth config to propagate, so we retry
          if (retryCount > 10) {
            throw new ISE(errMsg);
          } else {
            LOG.error(errMsg);
            LOG.error("retrying in 3000ms, retryCount: " + retryCount);
            retryCount++;
            Thread.sleep(3000);
          }
        } else {
          break;
        }
      }
      return response;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
