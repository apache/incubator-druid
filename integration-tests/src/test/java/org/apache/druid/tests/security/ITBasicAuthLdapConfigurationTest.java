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

package org.apache.druid.tests.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.apache.calcite.avatica.AvaticaSqlException;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.guice.annotations.Client;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.jackson.JacksonUtils;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.java.util.http.client.CredentialedHttpClient;
import org.apache.druid.java.util.http.client.HttpClient;
import org.apache.druid.java.util.http.client.auth.BasicCredentials;
import org.apache.druid.java.util.http.client.response.StatusResponseHolder;
import org.apache.druid.security.basic.authorization.entity.BasicAuthorizerGroupMapping;
import org.apache.druid.server.security.Action;
import org.apache.druid.server.security.Resource;
import org.apache.druid.server.security.ResourceAction;
import org.apache.druid.server.security.ResourceType;
import org.apache.druid.sql.avatica.DruidAvaticaHandler;
import org.apache.druid.testing.IntegrationTestingConfig;
import org.apache.druid.testing.clients.CoordinatorResourceTestClient;
import org.apache.druid.testing.guice.DruidTestModuleFactory;
import org.apache.druid.testing.utils.HttpUtil;
import org.apache.druid.testing.utils.ITRetryUtil;
import org.apache.druid.testing.utils.TestQueryHelper;
import org.apache.druid.tests.TestNGGroup;
import org.apache.druid.tests.indexer.AbstractIndexerTest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Test(groups = TestNGGroup.LDAP_SECURITY)
@Guice(moduleFactory = DruidTestModuleFactory.class)
public class ITBasicAuthLdapConfigurationTest
{
  private static final Logger LOG = new Logger(ITBasicAuthConfigurationTest.class);

  private static final TypeReference<List<Map<String, Object>>> SYS_SCHEMA_RESULTS_TYPE_REFERENCE =
      new TypeReference<List<Map<String, Object>>>()
      {
      };

  private static final String SYSTEM_SCHEMA_SEGMENTS_RESULTS_RESOURCE =
      "/results/auth_test_sys_schema_segments.json";
  private static final String SYSTEM_SCHEMA_SERVER_SEGMENTS_RESULTS_RESOURCE =
      "/results/auth_test_sys_schema_server_segments.json";
  private static final String SYSTEM_SCHEMA_SERVERS_RESULTS_RESOURCE =
      "/results/auth_test_sys_schema_servers.json";
  private static final String SYSTEM_SCHEMA_TASKS_RESULTS_RESOURCE =
      "/results/auth_test_sys_schema_tasks.json";

  private static final String SYS_SCHEMA_SEGMENTS_QUERY =
      "SELECT * FROM sys.segments WHERE datasource IN ('auth_test')";

  private static final String SYS_SCHEMA_SERVERS_QUERY =
      "SELECT * FROM sys.servers WHERE tier IS NOT NULL";

  private static final String SYS_SCHEMA_SERVER_SEGMENTS_QUERY =
      "SELECT * FROM sys.server_segments WHERE segment_id LIKE 'auth_test%'";

  private static final String SYS_SCHEMA_TASKS_QUERY =
      "SELECT * FROM sys.tasks WHERE datasource IN ('auth_test')";

  @Inject
  IntegrationTestingConfig config;

  @Inject
  ObjectMapper jsonMapper;

  @Inject
  @Client
  HttpClient httpClient;

  @Inject
  private CoordinatorResourceTestClient coordinatorClient;

  @BeforeMethod
  public void before()
  {
    // ensure that auth_test segments are loaded completely, we use them for testing system schema tables
    ITRetryUtil.retryUntilTrue(
        () -> coordinatorClient.areSegmentsLoaded("auth_test"), "auth_test segment load"
    );
  }

  @Test
  public void testSystemSchemaAccess() throws Exception
  {

    // initial setup is done now, run the system schema response content tests
    final List<Map<String, Object>> adminSegments = jsonMapper.readValue(
        TestQueryHelper.class.getResourceAsStream(SYSTEM_SCHEMA_SEGMENTS_RESULTS_RESOURCE),
        SYS_SCHEMA_RESULTS_TYPE_REFERENCE
    );

    final List<Map<String, Object>> adminTasks = jsonMapper.readValue(
        TestQueryHelper.class.getResourceAsStream(SYSTEM_SCHEMA_TASKS_RESULTS_RESOURCE),
        SYS_SCHEMA_RESULTS_TYPE_REFERENCE
    );

    final List<Map<String, Object>> adminServers = getServersWithoutCurrentSize(
        jsonMapper.readValue(
            fillServersTemplate(
                config,
                AbstractIndexerTest.getResourceAsString(SYSTEM_SCHEMA_SERVERS_RESULTS_RESOURCE)
            ),
            SYS_SCHEMA_RESULTS_TYPE_REFERENCE
        )
    );

    final List<Map<String, Object>> adminServerSegments = jsonMapper.readValue(
        fillSegementServersTemplate(
            config,
            AbstractIndexerTest.getResourceAsString(SYSTEM_SCHEMA_SERVER_SEGMENTS_RESULTS_RESOURCE)
        ),
        SYS_SCHEMA_RESULTS_TYPE_REFERENCE
    );



    HttpClient adminClient = new CredentialedHttpClient(
        new BasicCredentials("admin", "priest"),
        httpClient
    );

    // check that admin access works on all nodes
    checkNodeAccess(adminClient);

    // as admin
    LOG.info("Checking sys.segments query as admin...");
    verifySystemSchemaQuery(
        adminClient,
        SYS_SCHEMA_SEGMENTS_QUERY,
        adminSegments
    );

    LOG.info("Checking sys.servers query as admin...");
    verifySystemSchemaServerQuery(
        adminClient,
        SYS_SCHEMA_SERVERS_QUERY,
        getServersWithoutCurrentSize(adminServers)
    );

    LOG.info("Checking sys.server_segments query as admin...");
    verifySystemSchemaQuery(
        adminClient,
        SYS_SCHEMA_SERVER_SEGMENTS_QUERY,
        adminServerSegments
    );

    LOG.info("Checking sys.tasks query as admin...");
    verifySystemSchemaQuery(
        adminClient,
        SYS_SCHEMA_TASKS_QUERY,
        adminTasks
    );




    // datasourceOnlyUser tests
    // create a new user+role that can only read 'auth_test'
    List<ResourceAction> readDatasourceOnlyPermissions = Collections.singletonList(
        new ResourceAction(
            new Resource("auth_test", ResourceType.DATASOURCE),
            Action.READ
        )
    );

    createRoleWithPermissionsAndGroupMapping(
        adminClient,
        "datasourceOnlyGroup",
        ImmutableMap.of("datasourceOnlyRole", readDatasourceOnlyPermissions)
    );

    HttpClient datasourceOnlyUserClient = new CredentialedHttpClient(
        new BasicCredentials("datasourceOnlyUser", "helloworld"),
        httpClient
    );


    // check that we can access a datasource-permission restricted resource on the broker
    HttpUtil.makeRequest(
        datasourceOnlyUserClient,
        HttpMethod.GET,
        config.getBrokerUrl() + "/druid/v2/datasources/auth_test",
        null
    );

    // as user that can only read auth_test
    LOG.info("Checking sys.segments query as datasourceOnlyUser...");
    verifySystemSchemaQuery(
        datasourceOnlyUserClient,
        SYS_SCHEMA_SEGMENTS_QUERY,
        adminSegments.stream()
                     .filter((segmentEntry) -> "auth_test".equals(segmentEntry.get("datasource")))
                     .collect(Collectors.toList())
    );

    LOG.info("Checking sys.servers query as datasourceOnlyUser...");
    verifySystemSchemaQueryFailure(
        datasourceOnlyUserClient,
        SYS_SCHEMA_SERVERS_QUERY,
        HttpResponseStatus.FORBIDDEN,
        "{\"Access-Check-Result\":\"Insufficient permission to view servers : Allowed:false, Message:\"}"
    );

    LOG.info("Checking sys.server_segments query as datasourceOnlyUser...");
    verifySystemSchemaQueryFailure(
        datasourceOnlyUserClient,
        SYS_SCHEMA_SERVER_SEGMENTS_QUERY,
        HttpResponseStatus.FORBIDDEN,
        "{\"Access-Check-Result\":\"Insufficient permission to view servers : Allowed:false, Message:\"}"
    );

    LOG.info("Checking sys.tasks query as datasourceOnlyUser...");
    verifySystemSchemaQuery(
        datasourceOnlyUserClient,
        SYS_SCHEMA_TASKS_QUERY,
        adminTasks.stream()
                  .filter((taskEntry) -> "auth_test".equals(taskEntry.get("datasource")))
                  .collect(Collectors.toList())
    );






    // datasourceWithStateUser tests
    // create a new user+role that can only read 'auth_test' + STATE read access
    List<ResourceAction> readDatasourceWithStatePermissions = ImmutableList.of(
        new ResourceAction(
            new Resource("auth_test", ResourceType.DATASOURCE),
            Action.READ
        ),
        new ResourceAction(
            new Resource(".*", ResourceType.STATE),
            Action.READ
        )
    );

    createRoleWithPermissionsAndGroupMapping(
        adminClient,
        "datasourceWithStateGroup",
        ImmutableMap.of("datasourceWithStateRole", readDatasourceWithStatePermissions)
    );

    HttpClient datasourceWithStateUserClient = new CredentialedHttpClient(
        new BasicCredentials("datasourceWithStateUser", "helloworld"),
        httpClient
    );

    // check that we can access a state-permission restricted resource on the broker
    HttpUtil.makeRequest(
        datasourceWithStateUserClient,
        HttpMethod.GET,
        config.getBrokerUrl() + "/status",
        null
    );

    // as user that can read auth_test and STATE
    LOG.info("Checking sys.segments query as datasourceWithStateUser...");
    verifySystemSchemaQuery(
        datasourceWithStateUserClient,
        SYS_SCHEMA_SEGMENTS_QUERY,
        adminSegments.stream()
                     .filter((segmentEntry) -> "auth_test".equals(segmentEntry.get("datasource")))
                     .collect(Collectors.toList())
    );

    LOG.info("Checking sys.servers query as datasourceWithStateUser...");
    verifySystemSchemaServerQuery(
        datasourceWithStateUserClient,
        SYS_SCHEMA_SERVERS_QUERY,
        adminServers
    );

    LOG.info("Checking sys.server_segments query as datasourceWithStateUser...");
    verifySystemSchemaQuery(
        datasourceWithStateUserClient,
        SYS_SCHEMA_SERVER_SEGMENTS_QUERY,
        adminServerSegments.stream()
                           .filter((serverSegmentEntry) -> ((String) serverSegmentEntry.get("segment_id")).contains(
                               "auth_test"))
                           .collect(Collectors.toList())
    );

    LOG.info("Checking sys.tasks query as datasourceWithStateUser...");
    verifySystemSchemaQuery(
        datasourceWithStateUserClient,
        SYS_SCHEMA_TASKS_QUERY,
        adminTasks.stream()
                  .filter((taskEntry) -> "auth_test".equals(taskEntry.get("datasource")))
                  .collect(Collectors.toList())
    );







    // create a new user+role with only STATE read access
    List<ResourceAction> stateOnlyPermissions = ImmutableList.of(
        new ResourceAction(
            new Resource(".*", ResourceType.STATE),
            Action.READ
        )
    );
    createRoleWithPermissionsAndGroupMapping(
        adminClient,
        "stateOnlyGroup",
        ImmutableMap.of("stateOnlyRole", stateOnlyPermissions)
    );
    HttpClient stateOnlyUserClient = new CredentialedHttpClient(
        new BasicCredentials("stateOnlyUser", "helloworld"),
        httpClient
    );

    HttpUtil.makeRequest(stateOnlyUserClient, HttpMethod.GET, config.getBrokerUrl() + "/status", null);

    // as user that can only read STATE
    LOG.info("Checking sys.segments query as stateOnlyUser...");
    verifySystemSchemaQuery(
        stateOnlyUserClient,
        SYS_SCHEMA_SEGMENTS_QUERY,
        Collections.emptyList()
    );

    LOG.info("Checking sys.servers query as stateOnlyUser...");
    verifySystemSchemaServerQuery(
        stateOnlyUserClient,
        SYS_SCHEMA_SERVERS_QUERY,
        adminServers
    );

    LOG.info("Checking sys.server_segments query as stateOnlyUser...");
    verifySystemSchemaQuery(
        stateOnlyUserClient,
        SYS_SCHEMA_SERVER_SEGMENTS_QUERY,
        Collections.emptyList()
    );

    LOG.info("Checking sys.tasks query as stateOnlyUser...");
    verifySystemSchemaQuery(
        stateOnlyUserClient,
        SYS_SCHEMA_TASKS_QUERY,
        Collections.emptyList()
    );
  }

  @Test
  public void testAuthConfiguration() throws Exception
  {
    HttpClient adminClient = new CredentialedHttpClient(
        new BasicCredentials("admin", "priest"),
        httpClient
    );

    HttpClient internalSystemClient = new CredentialedHttpClient(
        new BasicCredentials("druid_system", "warlock"),
        httpClient
    );

    HttpClient newUserClient = new CredentialedHttpClient(
        new BasicCredentials("druid", "helloworld"),
        httpClient
    );

    final HttpClient unsecuredClient = httpClient;

    // check that we are allowed to access unsecured path without credentials.
    checkUnsecuredCoordinatorLoadQueuePath(unsecuredClient);

    // check that admin works
    checkNodeAccess(adminClient);

    // check that internal user works
    checkNodeAccess(internalSystemClient);

    // create a new user+role that can read /status
    List<ResourceAction> permissions = Collections.singletonList(
        new ResourceAction(
            new Resource(".*", ResourceType.STATE),
            Action.READ
        )
    );

    createRoleWithPermissionsAndGroupMapping(
        adminClient,
        "druidGroup",
        ImmutableMap.of("druidrole", permissions)
    );

    // check that the new user works
    checkNodeAccess(newUserClient);

    // check loadStatus
    checkLoadStatus(adminClient);

    String brokerUrl = "jdbc:avatica:remote:url=" + config.getBrokerUrl() + DruidAvaticaHandler.AVATICA_PATH;
    String routerUrl = "jdbc:avatica:remote:url=" + config.getRouterUrl() + DruidAvaticaHandler.AVATICA_PATH;

    LOG.info("Checking Avatica query on broker.");
    testAvaticaQuery(brokerUrl);

    LOG.info("Checking Avatica query on router.");
    testAvaticaQuery(routerUrl);

    LOG.info("Testing Avatica query on broker with incorrect credentials.");
    testAvaticaAuthFailure(brokerUrl);

    LOG.info("Testing Avatica query on router with incorrect credentials.");
    testAvaticaAuthFailure(routerUrl);

    LOG.info("Checking OPTIONS requests on services...");
    testOptionsRequests(adminClient);
  }

  @Test
  public void testInvalidAuthNames()
  {
    String invalidName = "invalid%2Fname";
    HttpClient adminClient = new CredentialedHttpClient(
        new BasicCredentials("admin", "priest"),
        httpClient
    );

    HttpUtil.makeRequestWithExpectedStatus(
        adminClient,
        HttpMethod.POST,
        StringUtils.format(
            "%s/druid-ext/basic-security/authentication/listen/%s",
            config.getCoordinatorUrl(),
            invalidName
        ),
        "SERIALIZED_DATA".getBytes(StandardCharsets.UTF_8),
        HttpResponseStatus.INTERNAL_SERVER_ERROR
    );

    HttpUtil.makeRequestWithExpectedStatus(
        adminClient,
        HttpMethod.POST,
        StringUtils.format(
            "%s/druid-ext/basic-security/authorization/listen/users/%s",
            config.getCoordinatorUrl(),
            invalidName
        ),
        "SERIALIZED_DATA".getBytes(StandardCharsets.UTF_8),
        HttpResponseStatus.INTERNAL_SERVER_ERROR
    );

    HttpUtil.makeRequestWithExpectedStatus(
        adminClient,
        HttpMethod.POST,
        StringUtils.format(
            "%s/druid-ext/basic-security/authorization/listen/groupMappings/%s",
            config.getCoordinatorUrl(),
            invalidName
        ),
        "SERIALIZED_DATA".getBytes(StandardCharsets.UTF_8),
        HttpResponseStatus.INTERNAL_SERVER_ERROR
    );
  }

  @Test
  public void testMaliciousUser()
  {
    String maliciousUsername = "<script>alert('hello')</script>";
    HttpClient maliciousClient = new CredentialedHttpClient(
        new BasicCredentials(maliciousUsername, "noPass"),
        httpClient
    );
    StatusResponseHolder responseHolder = HttpUtil.makeRequestWithExpectedStatus(
        maliciousClient,
        HttpMethod.GET,
        config.getBrokerUrl() + "/status",
        null,
        HttpResponseStatus.UNAUTHORIZED
    );
    String responseContent = responseHolder.getContent();
    Assert.assertTrue(responseContent.contains("<tr><th>MESSAGE:</th><td>Unauthorized</td></tr>"));
    Assert.assertFalse(responseContent.contains(maliciousUsername));
  }

  private void checkNodeAccess(HttpClient httpClient)
  {
    HttpUtil.makeRequest(httpClient, HttpMethod.GET, config.getCoordinatorUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.GET, config.getOverlordUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.GET, config.getBrokerUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.GET, config.getHistoricalUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.GET, config.getRouterUrl() + "/status", null);
  }

  private void checkLoadStatus(HttpClient httpClient) throws Exception
  {
    checkLoadStatusSingle(httpClient, config.getCoordinatorUrl());
    checkLoadStatusSingle(httpClient, config.getOverlordUrl());
    checkLoadStatusSingle(httpClient, config.getBrokerUrl());
    checkLoadStatusSingle(httpClient, config.getHistoricalUrl());
    checkLoadStatusSingle(httpClient, config.getRouterUrl());
  }

  private void checkLoadStatusSingle(HttpClient httpClient, String baseUrl) throws Exception
  {
    StatusResponseHolder holder = HttpUtil.makeRequest(
        httpClient,
        HttpMethod.GET,
        baseUrl + "/druid-ext/basic-security/authentication/loadStatus",
        null
    );
    String content = holder.getContent();
    Map<String, Boolean> loadStatus = jsonMapper.readValue(content, JacksonUtils.TYPE_REFERENCE_MAP_STRING_BOOLEAN);

    Assert.assertNotNull(loadStatus.get("ldap"));
    Assert.assertTrue(loadStatus.get("ldap"));

    holder = HttpUtil.makeRequest(
        httpClient,
        HttpMethod.GET,
        baseUrl + "/druid-ext/basic-security/authorization/loadStatus",
        null
    );
    content = holder.getContent();
    loadStatus = jsonMapper.readValue(content, JacksonUtils.TYPE_REFERENCE_MAP_STRING_BOOLEAN);

    Assert.assertNotNull(loadStatus.get("ldapauth"));
    Assert.assertTrue(loadStatus.get("ldapauth"));
  }

  private void checkUnsecuredCoordinatorLoadQueuePath(HttpClient client)
  {
    HttpUtil.makeRequest(client, HttpMethod.GET, config.getCoordinatorUrl() + "/druid/coordinator/v1/loadqueue", null);
  }

  private void testAvaticaQuery(String url)
  {
    LOG.info("URL: " + url);
    try {
      Properties connectionProperties = new Properties();
      connectionProperties.setProperty("user", "admin");
      connectionProperties.setProperty("password", "priest");
      Connection connection = DriverManager.getConnection(url, connectionProperties);
      Statement statement = connection.createStatement();
      statement.setMaxRows(450);
      String query = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS";
      ResultSet resultSet = statement.executeQuery(query);
      Assert.assertTrue(resultSet.next());
      statement.close();
      connection.close();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void testAvaticaAuthFailure(String url) throws Exception
  {
    LOG.info("URL: " + url);
    try {
      Properties connectionProperties = new Properties();
      connectionProperties.setProperty("user", "admin");
      connectionProperties.setProperty("password", "wrongpassword");
      Connection connection = DriverManager.getConnection(url, connectionProperties);
      Statement statement = connection.createStatement();
      statement.setMaxRows(450);
      String query = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS";
      statement.executeQuery(query);
    }
    catch (AvaticaSqlException ase) {
      Assert.assertEquals(
          ase.getErrorMessage(),
          "Error while executing SQL \"SELECT * FROM INFORMATION_SCHEMA.COLUMNS\": Remote driver error: BasicSecurityAuthenticationException: User LDAP authentication failed."
      );
      return;
    }
    Assert.fail("Test failed, did not get AvaticaSqlException.");
  }

  private void testOptionsRequests(HttpClient httpClient)
  {
    HttpUtil.makeRequest(httpClient, HttpMethod.OPTIONS, config.getCoordinatorUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.OPTIONS, config.getOverlordUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.OPTIONS, config.getBrokerUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.OPTIONS, config.getHistoricalUrl() + "/status", null);
    HttpUtil.makeRequest(httpClient, HttpMethod.OPTIONS, config.getRouterUrl() + "/status", null);
  }

  private void createRoleWithPermissionsAndGroupMapping(
      HttpClient adminClient,
      String group,
      Map<String, List<ResourceAction>> roleTopermissions
  ) throws Exception
  {
    roleTopermissions.keySet().forEach(role -> HttpUtil.makeRequest(
        adminClient,
        HttpMethod.POST,
        StringUtils.format(
            "%s/druid-ext/basic-security/authorization/db/ldapauth/roles/%s",
            config.getCoordinatorUrl(),
            role
        ),
        null
    ));

    for (Map.Entry<String, List<ResourceAction>> entry : roleTopermissions.entrySet()) {
      String role = entry.getKey();
      List<ResourceAction> permissions = entry.getValue();
      byte[] permissionsBytes = jsonMapper.writeValueAsBytes(permissions);
      HttpUtil.makeRequest(
          adminClient,
          HttpMethod.POST,
          StringUtils.format(
              "%s/druid-ext/basic-security/authorization/db/ldapauth/roles/%s/permissions",
              config.getCoordinatorUrl(),
              role
          ),
          permissionsBytes
      );
    }

    String groupMappingName = StringUtils.format("%sMapping", group);
    BasicAuthorizerGroupMapping groupMapping = new BasicAuthorizerGroupMapping(
        groupMappingName,
        StringUtils.format("cn=%s,ou=Groups,dc=example,dc=org", group),
        roleTopermissions.keySet()
    );
    byte[] groupMappingBytes = jsonMapper.writeValueAsBytes(groupMapping);
    HttpUtil.makeRequest(
        adminClient,
        HttpMethod.POST,
        StringUtils.format(
            "%s/druid-ext/basic-security/authorization/db/ldapauth/groupMappings/%s",
            config.getCoordinatorUrl(),
            groupMappingName
        ),
        groupMappingBytes
    );
  }

  private void verifySystemSchemaQuery(
      HttpClient client,
      String query,
      List<Map<String, Object>> expectedResults
  ) throws Exception
  {
    verifySystemSchemaQueryBase(client, query, expectedResults, false);
  }

  private void verifySystemSchemaQueryFailure(
      HttpClient client,
      String query,
      HttpResponseStatus expectedErrorStatus,
      String expectedErrorMessage
  ) throws Exception
  {
    StatusResponseHolder responseHolder = makeSQLQueryRequest(client, query, expectedErrorStatus);
    Assert.assertEquals(responseHolder.getStatus(), expectedErrorStatus);
    Assert.assertEquals(responseHolder.getContent(), expectedErrorMessage);
  }

  private void verifySystemSchemaQueryBase(
      HttpClient client,
      String query,
      List<Map<String, Object>> expectedResults,
      boolean isServerQuery
  ) throws Exception
  {
    StatusResponseHolder responseHolder = makeSQLQueryRequest(client, query, HttpResponseStatus.OK);
    String content = responseHolder.getContent();
    List<Map<String, Object>> responseMap = jsonMapper.readValue(content, SYS_SCHEMA_RESULTS_TYPE_REFERENCE);
    if (isServerQuery) {
      responseMap = getServersWithoutCurrentSize(responseMap);
    }
    Assert.assertEquals(responseMap, expectedResults);
  }

  private void verifySystemSchemaServerQuery(
      HttpClient client,
      String query,
      List<Map<String, Object>> expectedResults
  ) throws Exception
  {
    verifySystemSchemaQueryBase(client, query, expectedResults, true);
  }

  private StatusResponseHolder makeSQLQueryRequest(
      HttpClient httpClient,
      String query,
      HttpResponseStatus expectedStatus
  ) throws Exception
  {
    Map<String, Object> queryMap = ImmutableMap.of(
        "query", query
    );
    return HttpUtil.makeRequestWithExpectedStatus(
        httpClient,
        HttpMethod.POST,
        config.getBrokerUrl() + "/druid/v2/sql",
        jsonMapper.writeValueAsBytes(queryMap),
        expectedStatus
    );
  }

  /**
   * curr_size on historicals changes because cluster state is not isolated across different
   * integration tests, zero it out for consistent test results
   */
  private static List<Map<String, Object>> getServersWithoutCurrentSize(List<Map<String, Object>> servers)
  {
    return Lists.transform(
        servers,
        (server) -> {
          Map<String, Object> newServer = new HashMap<>(server);
          newServer.put("curr_size", 0);
          return newServer;
        }
    );
  }

  private static String fillSegementServersTemplate(IntegrationTestingConfig config, String template)
  {
    String json = StringUtils.replace(template, "%%HISTORICAL%%", config.getHistoricalInternalHost());
    return json;
  }

  private static String fillServersTemplate(IntegrationTestingConfig config, String template)
  {
    String json = StringUtils.replace(template, "%%HISTORICAL%%", config.getHistoricalInternalHost());
    json = StringUtils.replace(json, "%%BROKER%%", config.getBrokerInternalHost());
    json = StringUtils.replace(json, "%%NON_LEADER%%", String.valueOf(NullHandling.defaultLongValue()));
    return json;
  }
}
