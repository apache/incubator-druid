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

package org.apache.druid.security.basic.authentication.db.updater;

import org.apache.druid.security.basic.authentication.entity.BasicAuthenticatorCredentialUpdate;
import org.apache.druid.security.basic.authentication.entity.BasicAuthenticatorUser;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Empty implementation of {@link BasicAuthenticatorMetadataStorageUpdater}.
 * Void methods do nothing, other return empty maps or empty arrays depending on the return type.
 * The class is needed if a node should not support authenticator metadata storage update.
 */
public class NoopBasicAuthenticatorMetadataStorageUpdater implements BasicAuthenticatorMetadataStorageUpdater
{
  @Override
  public void createUser(String prefix, String userName)
  {
  }

  @Override
  public void deleteUser(String prefix, String userName)
  {
  }

  @Override
  public void setUserCredentials(String prefix, String userName, BasicAuthenticatorCredentialUpdate update)
  {
  }

  @Nullable
  @Override
  public Map<String, BasicAuthenticatorUser> getCachedUserMap(String prefix)
  {
    return Collections.emptyMap();
  }

  @Override
  public byte[] getCachedSerializedUserMap(String prefix)
  {
    return new byte[0];
  }

  @Override
  public byte[] getCurrentUserMapBytes(String prefix)
  {
    return new byte[0];
  }

  @Override
  public void refreshAllNotification()
  {
  }
}
