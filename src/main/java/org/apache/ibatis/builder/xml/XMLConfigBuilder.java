/**
 *    Copyright 2009-2021 the original author or authors.
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
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * XML配置构建器,继承baseBuilder(建造者模式).
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  // 标记是否已经解析过mybatis-config.xml配置文件.
  private boolean parsed;
  // 用于解析mybatis-config.xml配置文件的XPathParser对象.
  private final XPathParser parser;
  // 保存mybatis-config.xml中environment标签配置的名称.
  private String environment;
  // ReflectorFactory负责创建和缓存Reflector对象.
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  // 转换成XPathParser对象再去调用构造函数.
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    // 构造一个需要验证的XPathParser对象.
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    // 构建一个需要验证的XPathParser对象(这个过程会将配置文件的输入字节流转换为Document对象),并调用当前对象的下一个构造方法.
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  // 上面的6个构造函数最后都会走到这个函数,传入XPathParser对象.
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // 创建Configuration对象(这个过程初始化了很多属性),并将Configuration对象交给父类(BaseBuilder)的构造方法.
    super(new Configuration());
    // 错误上下文设置成SQL Mapper Configuration(xml文件配置).
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // 将输入进来的Properties全部添加到configuration中的variables对象中.
    this.configuration.setVariables(props);
    // 标记mybatis-config.xml文件为未被解析状态.
    this.parsed = false;
    // 保存mybatis-config.xml文件中定义的环境名称.
    this.environment = environment;
    // 保存XPathParser解析器对象.
    this.parser = parser;
  }

  // 解析mybatis-config.xml配置文件中的属性.
  public Configuration parse() {
    // 如果处于已解析过的状态,则抛出异常.
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 标记mybatis-config.xml配置文件已被解析过.
    parsed = true;
    // 解析mybatis-config.xml配置文件中的Configuration标签.
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  // 解析Configuration标签.
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析properties标签,将获取到的属性添加到Configuration中的variables属性中.
      propertiesElement(root.evalNode("properties"));
      // 解析settings标签,并检测在Configuration中是否定义了settings子标签中指定的属性名的setter()方法,如果没有则抛出异常.
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 根据用户在settings中指定的vfsImpl属性值,来设置指定的虚拟文件系统的具体实现类.
      loadCustomVfs(settings);
      // 根据用户在settings中指定的logImpl属性值,来设置指定的日志实现类.
      loadCustomLogImpl(settings);
      // 解析typeAliases标签.
      //  package: 将包名下的所有普通类(排除内部类,接口,抽象类),及@Alias注解中定义的value属性或Class类名的小写名称作为别名,添加到类型别名注册器中.
      //  other: alias-type: 将指定的别名与类型,添加到类型别名注册器中,如果alias不存在,则由@Alias注解中定义的value属性或Class类名的简称作为别名.
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析plugins标签,将每个plugin标签中定义的拦截器实例化,并添加到Configuration中的拦截器链对象中.
      pluginElement(root.evalNode("plugins"));
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 将settings标签中设置的各个属性,设置到Configuration对象中.
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析environment标签.
      //  创建transactionFactory,DataSourceFactory,DataSource对象,并封装为Environment对象赋值给Configuration中的environment对象.
      environmentsElement(root.evalNode("environments"));
      // 解析databaseIdProvider标签.
      //  通过dataSource对象连接中的产品名称,获取用户设置的几个数据库提供商名称,并将该名称赋值给Configuration中的databaseId对象.
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析typeHandlers标签.
      //  package: 将包名下实现了TypeHandler接口的所有普通类(排除内部类,接口,抽象类,最好使用@MappedJdbcTypes和@MappedTypes注解),添加到类型处理程序注册器中.
      //  other: javaType-jdbcType-handler: 将指定的类型处理程序类和对应可处理的jdbc类型,java类型,添加到类型处理程序注册器中.
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析mappers标签.
      //  将mapper接口与mapper.xml绑定,并注册到Configuration中的mapperRegistry集合中.
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    // 如果不存在settings标签,则返回空Properties对象.
    if (context == null) {
      return new Properties();
    }
    // 解析settings标签所有子节点的name和value属性,并封装到properties对象中.
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 创建configuration对应的MetaClass对象.
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 遍历props对象,检测Configuration中是否定义了指定的属性名的setter()方法.
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 遍历typeAliases标签的所有子标签.
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          // 如果子标签为package,则扫描该标签中的name属性指定的包名下的所有class文件,将所有普通类(排除内部类,接口,抽象类)及别名(小写类名或@Alias注解value属性中定义的值),添加到类型别名注册器中.
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 否则获取alias属性和type属性.
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              // 如果没有指定别名,则使用该Class的简称或@Alias注解中的value属性作为别名,与该Class对象一起添加到类型别名注册器中.
              typeAliasRegistry.registerAlias(clazz);
            } else {
              // 将别名与该Class对象添加到类型别名注册器中.
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历plugins标签的子标签,plugin.
      for (XNode child : parent.getChildren()) {
        // 获取子标签中的interceptor属性的值.
        String interceptor = child.getStringAttribute("interceptor");
        // 获取plugin内的所有property标签中定义的name-value.
        Properties properties = child.getChildrenAsProperties();
        // 根据别名获取Class对象,获取不到则使用类加载器去加载该class文件,并获取该Class的实例对象.
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        // 将plugin内的所有property标签中定义的name-value存储到拦截器对象中.
        interceptorInstance.setProperties(properties);
        // 将该拦截器对象添加到拦截器链中.
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    // 如果Configuration标签中存在properties标签.
    if (context != null) {
      // 获取properties标签所有子节点的name和value属性,并添加到Properties对象中(Properties对象是HashTable的子类).
      Properties defaults = context.getChildrenAsProperties();
      // 解析properties标签中的resource和url属性,这两个属性用于确定properties配置文件的位置.
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // resource和url只能同时存在一个.
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        // 通过resource获取.properties文件,并将获取到的name和value添加到Properties对象中.
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        // 通过url获取.properties文件,并将获取到的name和value添加到Properties对象中.
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      // 获取Configuration对象中的variables属性中的所有k-v属性.
      Properties vars = configuration.getVariables();
      if (vars != null) {
        // 将Configuration对象中的variables属性中的所有k-v属性添加到defaults中.
        defaults.putAll(vars);
      }
      // 刷新XPathParser和Configuration中的variables属性.
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      // 遍历查找id与XMLConfigBuilder.environment一致的environment标签.
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        // 判断是否为指定的environmentId.
        if (isSpecifiedEnvironment(id)) {
          // 解析当前environment标签中的transactionManager标签,并创建事务工厂对象.
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          // 解析当前environment标签中的dataSource标签,并创建数据源工厂和数据源对象.
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          // 获取数据源对象.
          DataSource dataSource = dsFactory.getDataSource();
          // 创建environment构建器对象,并对属性进行赋值.
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          // 通过environmentBuilder构建Environment对象,并赋值给Configuration中的environment属性.
          configuration.setEnvironment(environmentBuilder.build());
          break;
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      // 与老版本兼容
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    // 根据数据源对象获取当前DataSource对象标识.
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获取用户定义的事务工厂类型(JDBC).
      String type = context.getStringAttribute("type");
      // 获取transactionManager子标签中定义的property标签.
      Properties props = context.getChildrenAsProperties();
      // 根据指定的类型(Class对象的别名或Class的全局限定符)去创建事务工厂对象.
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 对该事务工厂设置自定义属性.
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 获取用户定义的数据源工厂类型.
      String type = context.getStringAttribute("type");
      // 获取dataSource子标签中定义的property标签.
      Properties props = context.getChildrenAsProperties();
      // 根据用户指定的数据源工厂类型(Class对象的别名或Class的全局限定符)去创建数据源工厂对象,并创建一个空的数据源对象.
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 对dataSource对象设置用户定义的属性.
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          // 获取指定包名下的接口类,并添加到映射关系注册器中.
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            try(InputStream inputStream = Resources.getResourceAsStream(resource)) {
              XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
              mapperParser.parse();
            }
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            try(InputStream inputStream = Resources.getUrlAsStream(url)){
              XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
              mapperParser.parse();
            }
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    return environment.equals(id);
  }

}
