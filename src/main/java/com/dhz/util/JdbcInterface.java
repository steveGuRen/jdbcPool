package com.dhz.util;

import java.sql.Connection;
import java.util.Map;

public interface JdbcInterface<T> {
	
	public T doQuery(Connection connection);
	
	public T doUpdate(Connection connection);
}
