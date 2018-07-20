package com.dhz.util;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dhz.jdbc.AbstractConnection;

public class TestPool {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestPool.class);

	public static AtomicLong i = new AtomicLong(0);
	
	@Test
	public void test() {
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
		JdbcConnectFactory.stopDaemonThread();
		PreparedStatement ptmt = null;
		ResultSet rs = null;
		int i = 1;
		while(true) {
			AbstractConnection asc = JdbcConnectFactory.getInstance().getConnection();
			Connection t = asc.getConnection();
			
			try {
				t.setAutoCommit(false);
				doQuery(ptmt, rs, t);
				t.commit();
				TestPool.i.addAndGet(1);
				System.out.println(TestPool.i.longValue());
				i++;
				if(i % 2000 == 0) {
					LOGGER.debug("Connection: " + t + "is closed");
					t.close();
				}
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
							JdbcConnectFactory.startDaemonThread();//启动修复进程
						}
					}
				} finally {
					
				}
			} finally {
				JdbcConnectFactory.releaseConnection(asc);
			}
//			Thread.sleep(3000);
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
//				System.out.println(a + " " + b + " " + c + " " + d);
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
	
	public static void main(String[] args) {
		new TestPool().test();
	}
}
