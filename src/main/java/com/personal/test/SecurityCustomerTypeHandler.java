package com.personal.test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * @auther: 胡鹏祥
 * @date: 2022/8/31
 * @description:
 */
public class SecurityCustomerTypeHandler extends BaseTypeHandler<String> {
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, encrypt(parameter));
  }

  private String encrypt(String parameter) {
    return "encrypt";
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    if(columnName.equals("job")){
      String value = rs.getString(columnName);
      return decrypt(value);
    }
    return rs.getString(columnName);
  }

  private String decrypt(String value) {
    return "decrypt";
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String value = rs.getString(columnIndex);
    return decrypt(value);
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String value = cs.getString(columnIndex);
    return decrypt(value);
  }
}
