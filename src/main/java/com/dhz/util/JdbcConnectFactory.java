package com.dhz.util;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author steve
 * 判断一个连接是否有效，应该是在连接执行SQL的时候进行判断，而不是在守护进程里面进行判断，守护进程是根据已经catch掉异常close的连接进行修复
 */
public class JdbcConnectFactory extends Thread{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectFactory.class);
	
	private static volatile boolean isStart = false;

	/**
	 * 可用连接<br><br>
	 * 不可以在事务线程里面对这个List进行update操作，只能进行ReadOnly的操作，否则可能出现守护线程遍历该list的时候大小异常<br>
	 * 只允许守护进程和单例初始化的时候对其进行update操作<br><br>
	 */
	private static List<Connection> synList = Collections.synchronizedList(new LinkedList<Connection>());

	/**
	 * 正在使用的连接<br>
	 * 事务线程才能对其进行update操作<br>
	 * 通过这个实现线程池里面connection的资源隔离
	 */
	private static Set<Connection> inUseList = Collections.synchronizedSet(new HashSet<Connection>());
	
	/**
	 * 单例，必须放置到所有属性定义的最后一个，防止因为类初始化时，类的成员变量初始化顺序不一致导致的问题
	 */
	private static final JdbcConnectFactory INSTANCE = new JdbcConnectFactory();
	
	
	private JdbcConnectFactory() {
		initialPool();
		LOGGER.info("jdbc connection initial is complete.");
		JdbcConnectFactory.isStart = true;
		this.start();
		LOGGER.info("pool daemon Thread is started.");
	}
	
	private void initialPool() {
		String url = JdbcConfig.getConfigProperty(JdbcConfig.CONNECT_URL);
		int minNum =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.MIN_NUM));
		Connection conn = null;
		try {
			Class.forName(JdbcConfig.getConfigProperty(JdbcConfig.JDBC_DRIVER));
			for(int i = 0; synList.size() + inUseList.size() < minNum; i++) {
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
	
	} 
			
	private Connection getConnection()	{
		if(synList.isEmpty()) {
			return null;
		} else {
			for(int i = 0; i < synList.size(); i++) {
				Connection connection = synList.get(i);
				if(inUseList.contains(connection) || connection == null) {
					continue;
				} else {
					if(LOGGER.isDebugEnabled()) {
						LOGGER.info("Thread " + Thread.currentThread().getName() + " get a connection :" + connection);
					}
					inUseList.add(connection);
					return connection;
				}
			}
			LOGGER.info("No connection can use in pool.All Connection is inused.");
			return null; //线程池满，无法再获取连接
			
		}
	}		
	
	private void releaseConnection(Connection connection) {
		if(connection == null) {
			return;
		} else {
			inUseList.remove(connection);			
		}
	}
	
	/**
	 * 守护线程，监控线程池连接状况，5S循环一次
	 * 
	 */
	@Override
	public void run() {
		int sleepTime =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.HEALTH_THREAD_SLEEPTIEM));
		while(isStart) {
			for(Iterator<Connection> i = synList.iterator(); i.hasNext();) {
				Connection conn = i.next();
				boolean isClose = false;
				boolean isNull = true;
				try {
					if(null == conn) {
						isNull = true;
					} else {
						isNull = false;
						isClose = conn.isClosed();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				if(isClose || isNull) {
					String url = JdbcConfig.getConfigProperty(JdbcConfig.CONNECT_URL);
					int minNum =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.MIN_NUM));
					try {
						Class.forName(JdbcConfig.getConfigProperty(JdbcConfig.JDBC_DRIVER));
						conn = DriverManager.getConnection(url);
					    if(LOGGER.isDebugEnabled()) {
					    	LOGGER.debug(conn.toString() + "is connected.");
					    }
					} catch (ClassNotFoundException | SQLException e) {
						e.printStackTrace();
					} finally {
						
					}
				}
			}
			try {
				Thread.sleep(sleepTime);
				if(LOGGER.isDebugEnabled()) {
					LOGGER.debug("Health thread is completed.");
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

	public static void main(String[] args) throws InterruptedException{
		for(int i = 0; i < 10; i++) {
			Thread thread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						RunSql();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
			thread.setName("THREAD " + i + ".");
			thread.start();
		}
		
	}

	private static void RunSql() throws InterruptedException {
		PreparedStatement ptmt = null;
		ResultSet rs = null;
		while(true) {
			Connection t = JdbcConnectFactory.INSTANCE.getConnection();
			try {
				t.setAutoCommit(false);
				doQuery(ptmt, rs, t);
				t.commit();
			} catch (SQLException e1) {
				e1.printStackTrace();  //SQL相关的异常抛出打印
				try {
					t.rollback();
				} catch (SQLException e) {
					if(null != t) {
						try {
							t.close();
						} catch (SQLException e2) {
							e2.printStackTrace();
						} finally {
							t = null;
						}
					}
				}
			} finally {
				
				JdbcConnectFactory.INSTANCE.releaseConnection(t);
			}
			LOGGER.debug("保持的线程池有：" + JdbcConnectFactory.synList.size());
			Thread.sleep(3000);
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
