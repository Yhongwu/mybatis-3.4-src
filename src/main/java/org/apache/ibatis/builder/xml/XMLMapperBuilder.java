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
import java.util.ArrayList;
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
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * 具体建造者
 */
public class XMLMapperBuilder extends BaseBuilder {

    private XPathParser parser;
    private MapperBuilderAssistant builderAssistant;
    private Map<String, XNode> sqlFragments;
    private String resource;

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

    /**
     * 解析映射文件入口
     */
    public void parse() {
        // 判断是否已经加载过该映射文件
        if (!configuration.isResourceLoaded(resource)) {
            // 处理<mapper>节点
            configurationElement(parser.evalNode("/mapper"));
            // 将 resource 添加到 Configuration.loadedResources 集合中保存，它是 HashSet<String>类型的集合，其中记录了已经加载过的映射文件
            configuration.addLoadedResource(resource);
            // 通过命名空间绑定(注册) Mapper 接口
            bindMapperForNamespace();
        }
        // 处理 configurationElement（）方法中解析失败的<resultMap>节点
        parsePendingResultMaps();
        // 处理 configurationElement（）方法中解析失败的<cache-ref>节点
        parsePendingCacheRefs();
        // 处理 configurationElement（）方法中解析失败的 SQL 语句节点
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    /**
     * 解析mapper文件
     * @param context
     */
    private void configurationElement(XNode context) {
        try {
            // 获取<mapper>节点的 namespace 属性
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.equals("")) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            // 设置 MapperBuilderAssistant 的 currentNamespace 字段，记录当前命名空间
            builderAssistant.setCurrentNamespace(namespace);
            // MyBatis 提供了一、二级缓存，其中一级缓存是 SqlSession 级别的，默认为开启状态。二级缓存配置在映射文件中，使用者需要显示配置才能开启
            // 解析cache-ref节点 是否公用二级缓存
            cacheRefElement(context.evalNode("cache-ref"));
            // 解析cache节点 二级缓存
            cacheElement(context.evalNode("cache"));
            // 解析parameterMap节点 已废弃
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            // 解析resultMap节点
            resultMapElements(context.evalNodes("/mapper/resultMap"));
            // 解析sql节点 <sql>用于保存sql片段 有databaseId属性 用于标明数据库厂商
            sqlElement(context.evalNodes("/mapper/sql"));
            // 解析select|insert|update|delete节点 这里暂且简称Statement节点
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            // 用重载方法构建 Statement
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            // 创建 Statement 建造类
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                // 解析Statement并存到Configuration的mappedStatements
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                // 解析失败，将解析器放入 configuration 的 incompleteStatements 集合中
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

    /**
     * 如果我们希望多个 namespace 共用同 一个二级缓存，
     * 即同一个 Cache 对象，则可以使用<cache-ref>节点进行配置。
     *
     * <mapper namespace="com.yhw.blog.dao.Mapper1">
     *     <!-- Mapper1 与 Mapper2 共用一个二级缓存 -->
     *     <cache-ref namespace="com.yhw.blog.dao.Mapper2"/>
     * </mapper>
     *
     * <!-- Mapper2.xml -->
     * <mapper namespace="com.yhw.blog.dao.Mapper2">
     *     <cache/>
     * </mapper>
     * @param context
     */
    private void cacheRefElement(XNode context) {
        if (context != null) {
            // 将当前 Mapper 配置文件的 namespace 与被引用的 Cache 所在的 namespace 之间的对应关系，记录到 Configuration.cacheRefMap 集合中
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            // 创建 CacheRefResolver 对象
            CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
            try {
                // 解析 Cache 引用，该过程主要是设置 MapperBuilderAssistant 中的currentCache 和 unresolvedCacheRef字段
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                // 如果解析过程出现异常 ，则添加到 Configuration.incompleteCacheRefs 集合， 稍后再解析
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    /**
     * MyBatis 拥有非常强大的二级缓存功能， 该功能可以非常方便地进行配置， MyBatis 默认情
     * 况下没有开启二级缓存，如果要为某命名空间开启 二级缓存功能，则需要在相应映射配置文件
     * 中添加＜cache＞节点，还可以通过配置＜cache＞节点的相关属性，为二级缓存配置相应的特性 （本
     * 质上就是添加相应的装饰器〉。
     * 比如：
     * <cache
     *   eviction="FIFO"
     *   flushInterval="60000"
     *   size="512"
     *   readOnly="true"/>
     *
     * @param context
     * @throws Exception
     */
    private void cacheElement(XNode context) throws Exception {
        if (context != null) {
            // 获取<cache>节点的 type 属性，默认是 PERPETUAL
            String type = context.getStringAttribute("type", "PERPETUAL");
            // 查找 type 属性对应的 Cache 接口实现类
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
            // 获取<cache>节点的 eviction 属性，默认是 LRU
            String eviction = context.getStringAttribute("eviction", "LRU");
            // 解析 eviction 属，性指定的 Cache 装饰器类型
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
            // 获取节点的 flushinterval 属性，默认值是 null
            Long flushInterval = context.getLongAttribute("flushInterval");
            // 获取节点的 size 属性，默认值是 null
            Integer size = context.getIntAttribute("size");
            // 获取节点的 readOnly 属性，默认值是 false
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            // 获取节点的 blocking 属性，默认值是 false
            boolean blocking = context.getBooleanAttribute("blocking", false);
            // 获取<cache>节点下的子节点，将用于初始化二级缓存
            Properties props = context.getChildrenAsProperties();
            // 通过 MapperBuilderAssistant 创建 Cache 对象，并添加到 Configuration.caches 集合中保存
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    private void parameterMapElement(List<XNode> list) throws Exception {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
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
                @SuppressWarnings("unchecked")
                Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    private void resultMapElements(List<XNode> list) throws Exception {
        // 遍历 <resultMap> 节点列表
        for (XNode resultMapNode : list) {
            try {
                // 解析 resultMap 节点
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
        // 调用重载方法
        return resultMapElement(resultMapNode, Collections.<ResultMapping>emptyList());
    }

    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        // 获取<resultMap>的 id 属性，默认值会取所有父节点的 id 或 value 或 property属性值
        String id = resultMapNode.getStringAttribute("id",
                resultMapNode.getValueBasedIdentifier());
        // 获取<resultMap>节点的 type 属性，表示结果将被映射成 type 指定类型的对象
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType",
                                resultMapNode.getStringAttribute("javaType"))));
        // 获取<resultMap>节点的 extends 属性，该属性指定了该<resultMap>节点的继承关系
        String extend = resultMapNode.getStringAttribute("extends");
        // 读取<resultMap>节点的 autoMapping 属性，将该属性设置为 true ，则启动自动映射功能，
        // 即自动查找与列名同名的属性名，并调用 setter 方法。 而设置为 false 后， 则需
        // 要在<resultMap>节点内明确注明映射关系才会调用对应 的 setter 方法 。
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        // 解析 type 类型
        Class<?> typeClass = resolveClass(type);
        Discriminator discriminator = null;
        // 该集合用于记录解析的结果
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        resultMappings.addAll(additionalResultMappings);
        // 处理 <resultMap>的子节点
        List<XNode> resultChildren = resultMapNode.getChildren();
        for (XNode resultChild : resultChildren) {
            if ("constructor".equals(resultChild.getName())) {
                // 处理constructor 节点
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                // 处理discriminator节点
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else {
                // 处理 <id>、<result>、<association>、<collection>
                List<ResultFlag> flags = new ArrayList<ResultFlag>();
                if ("id".equals(resultChild.getName())) {
                    // 如果是<id>节点，则向 flags 集合中添加 ResultFlag.ID
                    flags.add(ResultFlag.ID);
                }
                // 创建 ResultMapping 对象，并添加到 resultMappings 集合中保存
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            // 创建 ResultMap 对象，并添加到 Configuration.resultMaps 集合中，该集合是 StrictMap 类型
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            List<ResultFlag> flags = new ArrayList<ResultFlag>();
            flags.add(ResultFlag.CONSTRUCTOR);
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);
            }
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Map<String, String> discriminatorMap = new HashMap<String, String>();
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
            discriminatorMap.put(value, resultMap);
        }
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    private void sqlElement(List<XNode> list) throws Exception {
        if (configuration.getDatabaseId() != null) {
            // 调用 sqlElement 解析 <sql> 节点
            sqlElement(list, configuration.getDatabaseId());
        }
        // DatabaseId为null则再次调用 sqlElement 解析 <sql> 节点
        sqlElement(list, null);
    }

    private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
        for (XNode context : list) {
            // 获取 id 和 databaseId 属性
            String databaseId = context.getStringAttribute("databaseId");
            String id = context.getStringAttribute("id");
            // id = currentNamespace + "." + id
            id = builderAssistant.applyCurrentNamespace(id, false);
            // 检测当前 databaseId 和 requiredDatabaseId 是否一致
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                // 将 <id, XNode> 键值对缓存到 sqlFragments 中
                sqlFragments.put(id, context);
            }
        }
    }

    /**
     * requiredDatabaseId和databaseId是否匹配
     * @param id
     * @param databaseId
     * @param requiredDatabaseId
     * @return
     */
    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        //<!-- databaseId 不为空 -->
        //<sql id="table" databaseId="mysql">
        //    student
        //</sql>
        //
        //<!-- databaseId 为空 -->
        //<sql id="table">
        //    student
        //</sql>
        /**假设有这两个 第一个先被保存到sqlFragments了，而第二个保存的时候 发现已存在，且这次的requiredDatabaseId 为空，上次保存的DatabaseId不为空
         * 则返回false，不会保存到sqlFragments**/
        if (requiredDatabaseId != null) {
            // 不一样 返回false
            if (!requiredDatabaseId.equals(databaseId)) {
                return false;
            }
        } else {
            // 一个为null 一个不为null 返回false
            if (databaseId != null) {
                return false;
            }
            // skip this fragment if there is a previous one with a not null databaseId
            // 如果sql节点已经存在 且已存在的那个的databaseId不为null 返回false
            if (this.sqlFragments.containsKey(id)) {
                XNode context = this.sqlFragments.get(id);
                if (context.getStringAttribute("databaseId") != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
        String property;
        if (flags.contains(ResultFlag.CONSTRUCTOR)) {
            property = context.getStringAttribute("name");
        } else {
            property = context.getStringAttribute("property");
        }
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");
        String nestedResultMap = context.getStringAttribute("resultMap",
                processNestedResultMappings(context, Collections.<ResultMapping>emptyList()));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resultSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
        Class<?> javaTypeClass = resolveClass(javaType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
    }

    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
        if ("association".equals(context.getName())
                || "collection".equals(context.getName())
                || "case".equals(context.getName())) {
            if (context.getStringAttribute("select") == null) {
                ResultMap resultMap = resultMapElement(context, resultMappings);
                return resultMap.getId();
            }
        }
        return null;
    }

    private void bindMapperForNamespace() {
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
                //ignore, bound type is not required
            }
            if (boundType != null) {
                if (!configuration.hasMapper(boundType)) {
                    // Spring may not know the real resource name so we set a flag
                    // to prevent loading again this resource from the mapper interface
                    // look at MapperAnnotationBuilder#loadXmlResource
                    configuration.addLoadedResource("namespace:" + namespace);
                    configuration.addMapper(boundType);
                }
            }
        }
    }

}
