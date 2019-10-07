/**
 * Copyright ${license.git.copyrightYears} the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * BaseBuilder实现类
 * 解析 mybatis-config且nl 配置文件的入口，它通过调用
 * XMLConfigBuilder.parseConfiguration（）方法实现整个解析过程
 */
public class XMLConfigBuilder extends BaseBuilder {

    /**
     * 标识是否已经解析过 mybatis-config.xml 配置丈件
     */
    private boolean parsed;
    /**
     * 用于解析 mybatis-config.xml 配置文件的 XPathParser 对象
     */
    private XPathParser parser;
    /**
     * 标识＜ environment＞配置的名称，默认读取＜ environment＞标签的 default 属性
     */
    private String environment;
    /**
     *  负责创建和缓存 Reflector 对象
     */
    private ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    public Configuration parse() {
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        // 在mybatis-config.xml 配置文件中查找＜configuration＞节点，并开始解析
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 解析配置文件 最终解析到Configuration类
     * 参考 src\test\java\org\apache\ibatis\builder\MapperConfig.xml
     * @param root
     */
    private void parseConfiguration(XNode root) {
        try {
            //issue #117 read properties first
            // 解析各个节点
            propertiesElement(root.evalNode("properties"));

            //XMLConfigBuilder.settingsAsProperties（）方法负责解析＜settings＞节点，在＜settings＞节点下的
            //配置是 MyBatis 全局性的配置，它们会改变 MyBatis 的运行时行为，
            // 这些全局配置信息都会被记录到 Configuration 对象的对应属性中。
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            // 设置 vfsimpl 字段
            loadCustomVfs(settings);
            // 别名注册
            typeAliasesElement(root.evalNode("typeAliases"));
            // 插件解析
            pluginElement(root.evalNode("plugins"));
            objectFactoryElement(root.evalNode("objectFactory"));
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            // 将 settings 值设置到 Configuration 中
            settingsElement(settings);
            // read it after objectFactory and objectWrapperFactory issue #631
            // 环境配置
            environmentsElement(root.evalNode("environments"));
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            // 类型转换器解析
            typeHandlerElement(root.evalNode("typeHandlers"));
            // mapper解析
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        // 解析＜ settings ＞的子节点（＜ setting＞标签）的 name 和 value 属性，并返回 Properties 对象
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        // 创建 Configuration 对应的 MetaClass 对象
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        // 检测 key 指定的属性在 Configuration 类中是否有对应 se忧er 方法
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
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    /**
     * 别名注册 解析typeAliases节点
     * 有两种配置方式
     * <typeAliases>
     *     <package name="com.yhw.blog"/>
     * </typeAliases>
     * <!--如果使用者未配置 alias 属性，则需要 MyBatis 自行为目标类型生成别名。-->
     * <typeAliases>
     *     <typeAlias alias="student" type="com.yhw.blog.Student" />
     *     <typeAlias type="com.yhw.blog.Teacher" />
     * </typeAliases>
     * @param parent
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 处理<package>节点 从指定的包中解析别名和类型的映射
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    // 处理＜typeAlias＞节点
                    // 获取 alias 和 type 属性值，alias 不是必填项，可为空
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        // 加载 type 对应的类型
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            // 注册别名到类型的映射
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            // 注册别名
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    /**
     * 负责解析<plugins></plugins>节点中定义的插件
     * <plugins>
     *     <plugin interceptor="com.yhw.plugins.MyPlugin">
     *         <property name="key" value="value"/>
     *     </plugin>
     * </plugins>
     * @param parent
     * @throws Exception
     */
    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            // 追历全部子节点（即<plugin>节点）
            for (XNode child : parent.getChildren()) {
                // interceptor属性值
                String interceptor = child.getStringAttribute("interceptor");
                // 获取interceptor下properties属性并形成Properties
                Properties properties = child.getChildrenAsProperties();
                // 实例化interceptor插件
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
                //  设置 Interceptor 的属性
                interceptorInstance.setProperties(properties);
                // add到interceptorChain 插件链
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    /**
     * 通过添加自定义 Objectory 实现类、
     * ObjectWrapperFactory 实现类 以及 ReflectorFactory 实现类对 MyBatis 进行扩展。
     * @param context
     * @throws Exception
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    /**
     * 读取属性文件
     * 先从<property><property/>读取，后从url或resource读取，后者会覆盖前者，比如jdbc.properties会覆盖你标签里的定义内容
     * @param context
     * @throws Exception
     */
    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 解析＜properties ＞的子节点（＜property＞标签）的 name 和 value 属性，并记录到 Properties 中
            Properties defaults = context.getChildrenAsProperties();
            // 解析＜properties ＞的 resource 和 url ,It,性 ， 这两个属性用于确定 properties 配置文件的位置
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // resource 属性和 url 属性不能同时存在，否则会抛出异常
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            //加载 resource 或 url 指定的 properties 文件
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            // 与 Configuration 对象中的 variables 集合合并
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            // 更新 XPathParser 和 Configuration 的 variables 字段
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    private void settingsElement(Properties props) throws Exception {
        // 设置 autoMappingBehavior 属性，默认值为 PARTIAL
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
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        @SuppressWarnings("unchecked")
        Class<? extends Log> logImpl = (Class<? extends Log>) resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }

    /**
     * 环境相关配置，比如事务管理器和数据源
     * <environments default="development">
     *     <environment id="development">
     *         <transactionManager type="JDBC"/>
     *         <dataSource type="POOLED">
     *             <property name="driver" value="${jdbc.driver}"/>
     *             <property name="url" value="${jdbc.url}"/>
     *             <property name="username" value="${jdbc.username}"/>
     *             <property name="password" value="${jdbc.password}"/>
     *         </dataSource>
     *     </environment>
     * </environments>
     * @param context
     * @throws Exception
     */
    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            // 未指定 XMLConfigBuilder.environment 字段，则使用 default 属性指定的<environments>
            if (environment == null) {
                // 默认取default
                environment = context.getStringAttribute("default");
            }
            // 遍历子节点（即<environment>节点）
            for (XNode child : context.getChildren()) {
                // 获取id属性
                String id = child.getStringAttribute("id");
                // 与 XMLConfigBuilder.environment属性值 与 environment节点的id是否一致 一致就解析接下来的内容
                if (isSpecifiedEnvironment(id)) {
                    // 创建 TransactionFactory ，具体实现是先通过 TypeAliasRegistry 解析别名之后，再实例化TransactionFactory
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    // 创建 DataSourceFactory 和 DataSource 同上
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    // 创建 Environment, Environment 中封装了上面创建的 TransactionFactory 对象以及 DataSource 对象。 这里应用了建造者模式
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    private void databaseIdProviderElement(XNode context) throws Exception {
        // DummyDatabaseIdProvider和VendorDatabaseIdProvider实现类
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            //为了保证兼容性，修改 type 取值
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            // 创建 DatabaseidProvider 对象
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
            // 配置 DatabaseidProvider ，完成初始化
            databaseIdProvider.setProperties(properties);
        }
        // 通过前面确定的 DataSource 获取 databaseId ， 并记录到 configuration.databaseid 字段中
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            // 该方法会根据配置的数据库连接获取连接的数据库产品的名称
            // 再跟配置的databaseIdProvider 来匹配确定使用哪种数据库
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    /**
     * 解析TransactionFactory
     * 通过别名获取
     * @param context
     * @return
     * @throws Exception
     */
    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            // 获取type属性 如jdbc
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 实例化TransactionFactory
            TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     * 完成 TypeHandler 的注册
     * 数据库和java类型的映射转换 比如varchar对应java的string 可以自定义TypeHandler转换器
     * 同样配置方式有两种：
     * <!-- 自动扫描 -->
     * <typeHandlers>
     *     <package name="com.yhw.blog.handlers"/>
     * </typeHandlers>
     * <!-- 手动配置 -->
     * <typeHandlers>
     *     <typeHandler jdbcType="TINYINT"
     *             javaType="com.yhw.blog.enumerate.SexType"
     *             handler="com.yhw.blog.handler.SexTypeHandler"/>
     * </typeHandlers>
     *
     * 其中，自动配置的方式需要在类上加注解@MappedTypes和 @MappedJdbcTypes注解配置javaType和jdbcType,如
     *  @MappedTypes({Date.class})
     *  @MappedJdbcTypes(JdbcType.VARCHAR)
     *  public class MyDateTypeHandler extends BaseTypeHandler<Date>{...}
     * @param parent
     * @throws Exception
     */
    private void typeHandlerElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 从指定的包中注册 TypeHandler
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    // 获取 javaType，jdbcType 和 handler 属性值
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    // 根据 javaTypeClass 和 jdbcType 值的情况进行不同的注册策略
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            // register这个重载方法比较多
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

    /**
     * 解析mapper映射
     * 告诉 MyBatis 去哪些位置查找映射配置件以及使用了配置注解标识的接口。
     * 有以下几种配置方式:
     * 在 MyBatis 中，共有四种加载映射文件或信息的方式。
     * 1 从文件系统中加载映射文件；
     * 2 通过 URL 的方式加载和解析映射文件；
     * 3 通过 mapper 接口加载映射信息，映射信息可以配置在注解中，也可以配置在映射文件中。
     * 4 通过包扫描的方式获取到某个包下的所有类，并使用第三种方式为每个类解析映射信息。
     * <mappers>
     *   <mapper resource="org/mybatis/builder/StudentMapper.xml"/>
     *   <mapper resource="org/mybatis/builder/TeacherMapper.xml"/>
     * </mappers>
     * <mappers>
     *   <mapper url="file:///org/mybatis/builder/TeacherMapper.xml"/>
     * </mappers>
     * <mappers>
     *   <mapper class="org.mybatis.builder.StudentMapper"/>
     * </mappers>
     * <mappers>
     *   <package name="org.mybatis.builder"/>
     * </mappers>
     *
     * 遍历 mappers 的子节点，并根据节点属性值判断通过什么方式加载映射文件或映射信息
     * @param parent
     * @throws Exception
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    // 扫描指定的包，查找mapper接口,并向 MapperRegistry 注册 Mapper 接口
                    configuration.addMappers(mapperPackage);
                } else {
                    // 获取<mapper>节点的 resource 、 url 、 class 属性，这三个属性互斥
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        // 创建XMLMapperBuilder 对象，解析映射配置文件
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        // 解析映射文件 XMLMapperBuilder#parse 解析mapper文件入口
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        // 通过url加载配置, 创建XMLMapperBuilder 对象，解析映射配置文件
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        // 如果<mapper>节点指定了 class 属性 ，则向 MapperRegistry 注册该 Mapper 接口
                        // 通过 mapperClass 解析映射配置
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
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
