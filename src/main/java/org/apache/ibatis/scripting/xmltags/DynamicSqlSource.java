/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 保存动态SQL语句.
 *
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  // 存储MixedSqlNode对象,该对象中存储了select,update等标签中所有子标签对应的SqlNode对象.
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  // 得到绑定的sql.
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建DynamicContext对象,parameterObject是用户传入的实参.
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 调用MixedSqlNode对象的apply方法,
    // 该方法会遍历存储了该select,update等标签,以及各if,where子标签对应的SqlNode有序链表的apply方法,
    // 由各标签解释器解析自己标签对应的sql文本,并追加到传入的context中维护的StringJoiner字符串中.
    rootSqlNode.apply(context);
    // 创建SqlSourceBuilder,解析参数属性,并将SQL语句中的 #{} 占位符替换成 ? 占位符.
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 注意这里返回的是StaticSqlSource,解析完了就把那些参数都替换成 ? 了,也就是最基本的jdbc的sql写法.
    // 同时#{}占位符指定的参数,也已经解析完了,获取到了每个参数对应的javaType,jdbcType,TypeHandler等信息.
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 创建BoundSql对象,并将DynamicContext.bindings中的参数信息复制到其additionalParameters集合中保存.
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
