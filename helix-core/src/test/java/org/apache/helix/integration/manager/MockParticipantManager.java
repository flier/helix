package org.apache.helix.integration.manager;

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

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.helix.InstanceType;
import org.apache.helix.manager.zk.CallbackHandler;
import org.apache.helix.manager.zk.ZKHelixManager;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.mock.participant.DummyProcess.DummyLeaderStandbyStateModelFactory;
import org.apache.helix.mock.participant.DummyProcess.DummyOnlineOfflineStateModelFactory;
import org.apache.helix.mock.participant.MockMSModelFactory;
import org.apache.helix.mock.participant.MockSchemataModelFactory;
import org.apache.helix.mock.participant.MockTransition;
import org.apache.helix.participant.StateMachineEngine;
import org.apache.log4j.Logger;

public class MockParticipantManager extends ZKHelixManager implements Runnable, ZkTestManager {
  private static Logger LOG = Logger.getLogger(MockParticipantManager.class);

  private final CountDownLatch _startCountDown = new CountDownLatch(1);
  private final CountDownLatch _stopCountDown = new CountDownLatch(1);
  private final CountDownLatch _waitStopCompleteCountDown = new CountDownLatch(1);

  private final MockMSModelFactory _msModelFactory = new MockMSModelFactory(null);

  public MockParticipantManager(String zkAddr, String clusterName, String instanceName) {
    super(clusterName, instanceName, InstanceType.PARTICIPANT, zkAddr);
  }

  public void setTransition(MockTransition transition) {
    _msModelFactory.setTrasition(transition);
  }

  public void syncStop() {
    _stopCountDown.countDown();
    try {
      _waitStopCompleteCountDown.await();
    } catch (InterruptedException e) {
      LOG.error("exception in syncStop participant-manager", e);
    }
  }

  public void syncStart() {
    try {
      new Thread(this).start();
      _startCountDown.await();
    } catch (InterruptedException e) {
      LOG.error("exception in syncStart participant-manager", e);
    }
  }

  @Override
  public void run() {
    try {
      StateMachineEngine stateMach = getStateMachineEngine();
      stateMach.registerStateModelFactory("MasterSlave", _msModelFactory);

      DummyLeaderStandbyStateModelFactory lsModelFactory =
          new DummyLeaderStandbyStateModelFactory(10);
      DummyOnlineOfflineStateModelFactory ofModelFactory =
          new DummyOnlineOfflineStateModelFactory(10);
      stateMach.registerStateModelFactory("LeaderStandby", lsModelFactory);
      stateMach.registerStateModelFactory("OnlineOffline", ofModelFactory);

      MockSchemataModelFactory schemataFactory = new MockSchemataModelFactory();
      stateMach.registerStateModelFactory("STORAGE_DEFAULT_SM_SCHEMATA", schemataFactory);

      connect();
      _startCountDown.countDown();

      _stopCountDown.await();
    } catch (InterruptedException e) {
      String msg =
          "participant: " + getInstanceName() + ", " + Thread.currentThread().getName()
              + " is interrupted";
      LOG.info(msg);
    } catch (Exception e) {
      LOG.error("exception running participant-manager", e);
    } finally {
      _startCountDown.countDown();

      disconnect();
      _waitStopCompleteCountDown.countDown();
    }
  }

  @Override
  public ZkClient getZkClient() {
    return _zkclient;
  }

  @Override
  public List<CallbackHandler> getHandlers() {
    return _handlers;
  }
}
