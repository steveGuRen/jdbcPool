package com.dhz.util;

import java.sql.Connection;

/**
 * ��ͬ���̻߳��������connection���в���������Ҫ��֤connection���̰߳�ȫ��
 * @author steve
 *
 */
public class AbstractConnection {
	
	private volatile Connection connection = null;
	
	public AbstractConnection(Connection connection) {
		this.connection = connection;
	}
	
	public synchronized Connection getConnection() {
		return connection;
	}

	public synchronized void setConnection(Connection connection) {
		this.connection = connection;
	}
	
	
	
}
