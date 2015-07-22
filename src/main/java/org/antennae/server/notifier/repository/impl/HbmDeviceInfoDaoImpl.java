/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.antennae.server.notifier.repository.impl;

import javax.inject.Inject;

import org.antennae.server.notifier.entities.DeviceInfo;
import org.antennae.server.notifier.repository.IDeviceInfoDao;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class HbmDeviceInfoDaoImpl implements IDeviceInfoDao {
	
	@Inject
	private SessionFactory sessionFactory;

	@Override
	public void addDeviceInfo(DeviceInfo deviceInfo) {
		sessionFactory.getCurrentSession().saveOrUpdate(deviceInfo);
	}

	@Override
	public DeviceInfo getDeviceInfo(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void updateDeviceInfo(DeviceInfo deviceInfo) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteDeviceInfo(int id) {
		// TODO Auto-generated method stub

	}

}