package com.dhz.util;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dhz.jdbc.AbstractConnection;

/**
 * 
 * @author steve
 * �ж�һ�������Ƿ���Ч��Ӧ����������ִ��SQL��ʱ������жϣ����������ػ�������������жϣ��ػ������Ǹ����Ѿ�catch���쳣close�����ӽ����޸�
 */
public class JdbcConnectFactory extends Thread implements DataSource{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnectFactory.class);
	
	private static volatile boolean isStart = false;

	/**
	 * ��������<br><br>
	 * �������������߳���������List����update������ֻ�ܽ���ReadOnly�Ĳ�����������ܳ����ػ��̱߳�����list��ʱ���С�쳣<br>
	 * ֻ�����ػ����̺͵�����ʼ����ʱ��������update����<br><br>
	 */
	private static List<AbstractConnection> synList = Collections.synchronizedList(new LinkedList<AbstractConnection>());

	/**
	 * ����ʹ�õ�����<br>
	 * �����̲߳��ܶ������update����<br>
	 * ͨ�����ʵ���̳߳�����connection����Դ����
	 */
	private static Set<AbstractConnection> inUseList = Collections.synchronizedSet(new HashSet<AbstractConnection>());
	
	/**
	 * ������������õ��������Զ�������һ������ֹ��Ϊ���ʼ��ʱ����ĳ�Ա������ʼ��˳��һ�µ��µ�����
	 */
	private static final JdbcConnectFactory INSTANCE = new JdbcConnectFactory();
	
	
	private JdbcConnectFactory() {
		initialPool();
		LOGGER.info("jdbc connection initial is complete.");
		JdbcConnectFactory.isStart = false;
		this.setName("DAEMON THREAD");
		 this.setDaemon(true);
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
				AbstractConnection asc = new AbstractConnection(conn);
				if(LOGGER.isDebugEnabled()) {
				    	LOGGER.debug(asc.toString() + " is connected. ");
				}
				synList.add(asc);
			}
			if(LOGGER.isDebugEnabled()) {
				LOGGER.debug("Connections in pool is inited.");
			}
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		} finally {
			
		}
	
	} 
	
	public static JdbcConnectFactory getInstance() {
		return INSTANCE;
	}
	
	public AbstractConnection getConnection()	{
		if(synList.isEmpty()) {
			return null;
		} else {
			for(int i = 0; i < synList.size(); i++) {
				AbstractConnection asc = synList.get(i);
				Connection connection = asc.getConnection();
				boolean isClosed = true;
				try {
					isClosed = connection.isClosed();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(inUseList.contains(asc) || connection == null || isClosed) {
					continue;
				} else {
					if(LOGGER.isDebugEnabled()) {
						LOGGER.info("Thread " + Thread.currentThread().getName() + " get a connection (" + connection + ") in AbstractConnection(" + asc + ")");
					}
					inUseList.add(asc);
					return asc;
				}
			}
			LOGGER.info("No connection can use in pool.All Connection is inused.");
			return null; //�̳߳������޷��ٻ�ȡ����
			
		}
	}		
	
	public static void releaseConnection(AbstractConnection asc) {
		if(asc == null) {
			return;
		} else {
			inUseList.remove(asc);			
		}
	}
	
	public static boolean isDaemonThreadStarted() {
		return isStart;
	}
	
	public static void startDaemonThread() {
		isStart = true;
	}
	
	public static void stopDaemonThread() {
		isStart = false;
	}
	/**
	 * �ػ��̣߳�����̳߳�����״����5Sѭ��һ��
	 * 
	 */
	@Override
	public void run() {
		int sleepTime =  NumberUtils.toInt(JdbcConfig.getConfigProperty(JdbcConfig.HEALTH_THREAD_SLEEPTIEM));
		while(true) {
			for(Iterator<AbstractConnection> i = synList.iterator(); i.hasNext() && isStart;) {
				AbstractConnection asc = i.next();
				Connection conn = asc.getConnection();
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
						asc.setConnection(conn);
					    if(LOGGER.isDebugEnabled()) {
					    	LOGGER.debug(asc.toString() + " is reconnected. ");
					    }
					} catch (ClassNotFoundException | SQLException e) {
						e.printStackTrace();
					} finally {
						
					}
				}
			}
			try {
				if(LOGGER.isDebugEnabled()) {
					LOGGER.debug("Health thread is completed.");
					LOGGER.debug("connected number:" + (JdbcConnectFactory.inUseList.size()));
					LOGGER.debug("remaid number:" + (JdbcConnectFactory.synList.size() - JdbcConnectFactory.inUseList.size()));
					
				}
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Thread.yield();
		}
		
	}

	
	
	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return new PrintWriter(System.out);
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		return;
		
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		// TODO Auto-generated method stub
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		// TODO Auto-generated method stub
		return java.util.logging.Logger.getAnonymousLogger();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return getConnection();
	}
	
	
}
