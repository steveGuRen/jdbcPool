package com.dhz.util;

import java.util.ResourceBundle;

/**
 * 可配置枚举定义<br>
 * avaliable configuration define<br>
 * 
 * @author steve
 *
 */
public class JdbcConfig {
	private static final String DEFAULT_REDIS_PROPERTIES = "jdbcPool";
	private static ResourceBundle JDBC_POOL = ResourceBundle.getBundle(DEFAULT_REDIS_PROPERTIES);

	public static String getConfigProperty(String key) {
		return JDBC_POOL.getString(key);
	}

	/**
	 * 连接URL<br>
	 * 
	 */
	public final static String CONNECT_URL = "CONNECT_URL";
	public final static String JDBC_DRIVER = "JDBC_DRIVER";
	public final static String MIN_NUM = "MIN_NUM";
	public final static String MAX_NUM = "MAX_NUM";
	public final static String HEALTH_THREAD_SLEEPTIEM = "HEALTH_THREAD_SLEEPTIEM";

}
