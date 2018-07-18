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
 * �ж�һ�������Ƿ���Ч��Ӧ����������ִ��SQL��ʱ������жϣ����������ػ�������������жϣ��ػ������Ǹ����Ѿ�catch���쳣close�����ӽ����޸�
 */
public class JdbcConnectFactory extends Thread{
	
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
			
	private AbstractConnection getConnection()	{
		if(synList.isEmpty()) {
			return null;
		} else {
			for(int i = 0; i < synList.size(); i++) {
				AbstractConnection asc = synList.get(i);
				Connection connection = asc.getConnection();
				if(inUseList.contains(asc) || connection == null) {
					continue;
				} else {
					if(LOGGER.isDebugEnabled()) {
						LOGGER.info("Thread " + Thread.currentThread().getName() + " get a connection :" + connection);
					}
					inUseList.add(asc);
					return asc;
				}
			}
			LOGGER.info("No connection can use in pool.All Connection is inused.");
			return null; //�̳߳������޷��ٻ�ȡ����
			
		}
	}		
	
	private void releaseConnection(AbstractConnection asc) {
		if(asc == null) {
			return;
		} else {
			inUseList.remove(asc);			
		}
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
		for(int i = 1; i < 10; i++) {
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
			thread.setName("THREAD denghuizhi" + i + ".");
			thread.start();
		}
		
	}

	private static void RunSql() throws InterruptedException {
		PreparedStatement ptmt = null;
		ResultSet rs = null;
		int i = 1;
		while(true) {
			AbstractConnection asc = JdbcConnectFactory.INSTANCE.getConnection();
			Connection t = asc.getConnection();
			try {
				t.setAutoCommit(false);
				doQuery(ptmt, rs, t);
				t.commit();
				i++;
				if(i % 5 == 0) {
					LOGGER.debug("Connection: " + t + "is closed");
					t.close();
				}
			} catch (SQLException e1) {
				e1.printStackTrace();  //SQL��ص��쳣�׳���ӡ
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
							JdbcConnectFactory.isStart = true;//�����޸�����
						}
					}
				} finally {
					
				}
			} finally {
				JdbcConnectFactory.INSTANCE.releaseConnection(asc);
			}
			LOGGER.debug("connected number:" + (JdbcConnectFactory.synList.size() - JdbcConnectFactory.inUseList.size()));
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
