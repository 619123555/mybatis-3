/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 *
 * 解析mapper.xml中的各个标签,并将配置信息传给MapperBuilderAssistant中定义的函数,来创建相应的对象.
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  // xml构建助手,存储了关于当前mapper.xml中的所有信息,包括各个select,update,insert,delete标签的MapperStatement对象.
  private final MapperBuilderAssistant builderAssistant;
  // 用来存放用户在mapper中定义的可重用的sql片段(sql标签).
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  public void parse() {
    // 判断是否已成功解析过该mapper.xml文件.
    //  用户在mapper-config.xml -> mappers标签中的mapper标签中的resource属性指定的mapper.xml文件路径时为 mappers/empDao.xml.
    //  用户在mapper-config.xml -> mappers标签中的package标签指定的目录中的mapper.xml文件路径时为 Class的全局限定符.xml.
    if (!configuration.isResourceLoaded(resource)) {
      // 解析mapper.xml中的所有标签,并创建一些对象,添加到Configuration中.
      configurationElement(parser.evalNode("/mapper"));

      // 将namespace添加到configuration中的loadedResource集合中,表示已加载过该namespace的mapper.xml.
      configuration.addLoadedResource(resource);
      // 加载mapper.xml中namespace指定的Mapper接口对象,并将该Class与Mapper工厂对象绑定,一起添加到knownMappers中
      // 注: 该操作是为了加载 通过mappers标签中的mapper标签中指定了resource或url方式的 mapper的Class文件.
      bindMapperForNamespace();
    }

    // 尝试对所有未完成ResultMap解析的对象,再次进行解析.
    //  在解析resultMap标签时,可能会因为使用了extends属性,并且依赖的父ResultMap标签未加载完成,导致无法处理,所以暂时加入未完成队列中.
    parsePendingResultMaps();
    // 尝试对所有未完成cache-ref配置的对象,再次进行解析.
    //  在解析cache-ref标签时,由于指定的namespace对应的Cache对象还未创建,导致无法配置cache-ref指定的Cache对象,所以在每个mapper解析完成后都遍历一遍该集合,尝试配置.
    //  注意: 这个时候是会覆盖根据cache标签 或 CacheNamespace注解 创建的Cache对象.
    parsePendingCacheRefs();
    // 尝试对所有未完成select,update,insert,delete标签解析的MapperBuilderAssistant,再次进行解析.
    //  在解析select,update,insert,delete标签时,如果当前mapper的cacheRef标签未解析完成,则先将MapperBuilderAssistant对象添加到未完成队列中.
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  private void configurationElement(XNode context) {
    try {
      // 获取mapper.xml -> mapper标签中的namespace属性(Class的全局限定符).
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // 将namespace添加到builderAssistant对象中,方便后续使用.
      // 这里会校验该xml中的namespace属性是否与当前Class接口的全局限定符一致,不一致则会抛出异常.
      builderAssistant.setCurrentNamespace(namespace);
      // 解析cache-ref标签,并将指定的namespace的mapper.xml对应的Cache对象应用到当前mapper中.
      // 这样当前mapper与被引用mapper的操作,会互相影响对方的缓存,因为用的是同一个Cache对象.
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析cache标签,并创建Cache对象.
      // 注: 当与cache-ref标签共同使用时,最终会使用哪个Cache对象是不确定的,因为cache-ref解析失败后,会在每个mapper解析完成后,再次尝试覆盖.
      //     cache-ref标签 与 CacheNamespaceRef注解 是可以同时使用的,
      //     正常情况最终是使用的CacheNamespaceRef注解中指定的namespace的Cache对象,因为注解形式的未完成CacheRef解析对象是最后放进去的,会将前边的给覆盖.
      cacheElement(context.evalNode("cache"));
      // 解析parameterMap标签(非select标签中的parameterMap,已弃用).
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析resultMap标签(mepper.xml中配置mysql字段与java实体映射关系的那个ResultMap标签).
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析sql标签(可重用sql代码段).
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析select,update,insert,delete等标签,为每个标签创建对应的MappedStatement对象.
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      // 如果sql标签指定了数据库id,则提前走一遍解析sql语句逻辑.
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    // 开始走正常处理sql语句流程.
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      // 创建sql语句构建器对象.
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 解析sql语句标签(<select|update|insert|delete>).
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  // 使用cache-ref标签时,可以定义namespace属性来引用另外一个缓存.
  //  <cache-ref namespace="com.someone.application.data.SomeMapper"/>
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // 将当前mapper.xml文件中的namespace与被引用的cache所在的namespace之间的对应关系,记录到configuration.cacheRefMap集合中.
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 创建CacheRefResolver对象.
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // 获取cache-ref标签中指定的namespace属性对应的缓存对象,并将该缓存规则应用到当前mapper.
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // 如果解析过程中出现异常,则添加到Configuration.incompleteCacheRef集合.
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  // <mapper>
  //  <cache eviction="FIFO" flushInterval="60000" size="512" readOnly="true">
  // </mapper>
  private void cacheElement(XNode context) {
    if (context != null) {
      // 获取cache节点的type属性,来设置缓存时效,默认PerpetualCache类(永久缓存).
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // 获取eviction属性,来设置缓存淘汰策略,默认LruCache(最近最少使用).
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // 获取flushInterval属性,默认是null(永不刷新).
      Long flushInterval = context.getLongAttribute("flushInterval");
      // 获取size属性,默认值是null(无限制).
      Integer size = context.getIntAttribute("size");
      // 获取readOnly属性,默认false.
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // 获取blocking属性,默认false.
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // 获取cache标签的子标签(properties),用于初始化二级缓存.
      Properties props = context.getChildrenAsProperties();
      // 通过MapperBuilderAssistant创建Cache对象,并添加到Configuration.caches集合中保存.
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  // 已经被废弃了!老式风格的参数映射,可以忽略.
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) {
    // 循环把resultMap子节点加入到Configuration.
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
        // 忽略异常.
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) {
    // 错误上下文.
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 获取resultMap节点的type属性,表示ResultMap将被映射成type指定类型的对象.
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    // 该集合用于记录当前resultMap中的子标签,及?????
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
    // 遍历resultMap的子标签,挨个儿解析.
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        // 处理constructor标签,并将解析该标签得到的所有ResultMapping对象添加到集合中.
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        // 解析discriminator(鉴别器)标签.
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // 处理id,result,association,collection等子标签.
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          // 如果是id节点,则向flags集合中添加ResultFlag.ID.
          flags.add(ResultFlag.ID);
        }
        // 根据子标签的配置,创建对应的ResultMapping对象,并添加到resultMappings集合中保存.
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // 获取resultMap的id属性,默认值会拼装所有标签的id或value或property属性值.
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    // 获取resultMap节点的extends属性,该属性指定了当前resultMap标签继承哪个resultMap标签.
    String extend = resultMapNode.getStringAttribute("extends");
    // 读取resultMap节点的autoMapping属性.
    //  true,则启动自动映射功能,即自动查找与列名同名的属性值,并调用setter方法.
    //  false,则需要在resultMap节点内明确标注映射关系才会调用对应的setter方法.
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // 通过ResultMapResolver的resolve()去解析,并创建ResultMap对象.
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // 创建ResultMap对象,并添加到resultMap集合中,该集合是StrictMap类型.
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      // 可能由于当前ResultMap依赖的父ResultMap标签尚未解析完成,所以先加入队列中,后续再次进行解析.
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  // 解析resultMap标签中的constructor标签.
  //  <constructor>
  //    <idArg column="blog_id" javaType="int"/>
  //  </constructor>
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    // 获取constructor节点的子节点.
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      // 添加CONSTRUCTOR标志.
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        // 对于idArgs节点,添加id标志.
        flags.add(ResultFlag.ID);
      }
      // 创建ResultMapping对象,并添加到resultMappings集合中.
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  // 解析resultMap标签中的discriminator标签.
  //  <discriminator javaType="int" column="draft">
  //    <case value="1" resultMap="DraftPost"/>
  //  </discriminator>
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
    // 获取column,javaType,jdbcType,typeHandler属性.
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 暂时存储所有case的id,及ResultMap的Id.
    Map<String, String> discriminatorMap = new HashMap<>();
    // 处理discriminator节点的子节点.
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      // 调用processNestedResultMappings方法创建嵌套的ResultMap对象.
      // 获取resultMap属性记录的ResultMapId,或根据当前标签的resultMap子标签创建一个ResultMap对象出来,并返回ResultMapId.
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      // 记录该case的value,ResultMap的Id.
      discriminatorMap.put(value, resultMap);
    }
    // 构建discriminatorMap对象.
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  // 解析sql标签(用户定义的可重用的SQL代码段).
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  // 解析sql标签(用户定义的可重用的SQL代码段).
  // <sql id="userColumns"> id,username,password </sql>
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    // 遍历处理sql标签.
    for (XNode context : list) {
      // 获取databaseId属性.
      String databaseId = context.getStringAttribute("databaseId");
      // 获取id属性.
      String id = context.getStringAttribute("id");
      // 为id添加命名空间.
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 检测sql的databaseId与当前configuration中记录的databaseId是否一致.
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // 记录到XMLMapperBuilder.sqlFragments中保存,在XMLMapperBuilder的构造函数中,可以看到该字段执行了XMLMapperBuilder.sqlFragments集合.
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  // 构建ResultMapping对象.
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      // 如果是constructor标签中的子标签,并且使用了select时,
      // column字段会用在select指定的sql的参数名称上,所以需要通过name属性来标记java字段名称.
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    // 获取column,javaType,jdbcType,select等属性值.
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    // resultMap标签 -> association标签中的属性.
    // 指定MapperStatement的Id, 在获取到当前sql结果后,根据结果中的某个字段去执行select指定的MapperStatement.
    String nestedSelect = context.getStringAttribute("select");
    // 先解析嵌套的resultMap标签.
    String nestedResultMap = context.getStringAttribute("resultMap", () ->
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    // 解析javaType,typeHandler,jdbcType.
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 创建一个完整的,可直接使用的ResultMapping对象.
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  // 处理嵌套的resultMap(association|collection| discriminatorMap -> case的子标签, 都属于嵌套ResultMap).
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
    // 处理association|collection| discriminatorMap -> case标签.
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
        && context.getStringAttribute("select") == null) {
      validateCollection(context, enclosingType);
      // 创建ResultMap对象,并添加到Configuration.resultMaps集合中.
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
            "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  private void bindMapperForNamespace() {
    // 获取mapper.xml文件中的命名空间.
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        // 加载命名空间对应的Class.
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required
      }
      // 校验是否已加载过mapper.xml中namespace指定的mapper接口(knowMapper).
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        // 在当前Class全局限定符前追加namespace,并添加到loadedResources集合中保存,表示已加载过.
        configuration.addLoadedResource("namespace:" + namespace);
        // 加载mapper.xml中namespace指定的mapper接口类.
        // 创建Mapper接口与对应的MapperProxyFactory对象,并添加到knowMapper中.
        configuration.addMapper(boundType);
      }
    }
  }

}
