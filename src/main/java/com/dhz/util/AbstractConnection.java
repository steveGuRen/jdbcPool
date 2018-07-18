package com.dhz.util;

import java.sql.Connection;

/**
 * 不同的线程会对这个类的connection进行操作，所以要保证connection是线程安全的
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
