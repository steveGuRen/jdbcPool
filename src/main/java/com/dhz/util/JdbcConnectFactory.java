package com.dhz.util;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcConnectFactory {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectFactory.class);
	
	private static List<Connection> synList = Collections.synchronizedList(new LinkedList<Connection>());

	private static final JdbcConnectFactory INSTANCE = new JdbcConnectFactory();
	
	
	private JdbcConnectFactory() {
		initialPool();
	}
	
	private void initialPool() {
		String url = JdbcConfig.getConfigProperty(JdbcConfig.CONNECT_URL);
		int minNum =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.MIN_NUM));
		Connection conn = null;
		try {
			Class.forName(JdbcConfig.getConfigProperty(JdbcConfig.JDBC_DRIVER));
			for(int i = 0; i < minNum; i++) {
				conn = DriverManager.getConnection(url);
				if(LOGGER.isDebugEnabled()) {
					LOGGER.debug(conn.toString() + "is connected.");
				}
				synList.add(conn);
			}
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		} finally {
			
		}
		LOGGER.info("jdbc connection initial is complete.");
	} 
			
	private Connection getConnection()	{
		if(synList.isEmpty()) {
			return null;
		} else {
			Connection connection = synList.remove(0);
			return connection;
		}
	}		
	
	private void releaseConnection(Connection connection) {
		
		if(connection == null) {
			return;
		} else {
			try {
				boolean isClose = connection.isClosed();
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				initialPool();
			}
		}
		int maxNum =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.MAX_NUM));
		if(synList.size() < maxNum) {
			
			synList.add(connection);
		}
	}
	
	public static void main(String[] args){
		PreparedStatement ptmt = null;
		ResultSet rs = null;
		Connection t = JdbcConnectFactory.INSTANCE.getConnection();
		try {
			t.setAutoCommit(false);
			doQuery(ptmt, rs, t);
			t.commit();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			try {
				t.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			throw new RuntimeException(e1);
		}
		
	}

	private static void doQuery(PreparedStatement ptmt, ResultSet rs, Connection t) throws SQLException {
		try {
			ptmt = t.prepareStatement("select * from dlock");
			rs = ptmt.executeQuery();
			while (rs.next()) {
				String a = rs.getString("id");
				String b = rs.getString("key");
				Date c = rs.getDate("createTime");
				Date d = rs.getDate("updateTime");
				System.out.println(a);
				System.out.println(b);
				System.out.println(c);
				System.out.println(d);
			}
		} catch (SQLException e) {
		} finally {
			if (rs != null) {
				rs.close();
			}
			if (ptmt != null)
				ptmt.close();
		}
	}
}
