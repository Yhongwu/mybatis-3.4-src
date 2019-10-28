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
package org.apache.ibatis.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.Jdk;
import org.apache.ibatis.reflection.ParamNameUtil;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ResultMap {
    private Configuration configuration;

    /**
     * <resultMap></resultMap>节点的 id 属性
     */
    private String id;
    /**
     * type 属性
     */
    private Class<?> type;
    /**
     * 记录了除 <discriminator></discriminator>节点之外的其他映射关系（即 ResultMapping 对象集合）
     */
    private List<ResultMapping> resultMappings;
    /**
     * 记录了映射关系中带有 ID 标志的映射关系，例如 <id></id>节点和 <constructor></constructor>节点的<idArg></idArg>子节点
     */
    private List<ResultMapping> idResultMappings;
    /**
     * 记录了映射关系中带有 Constructor 标志的映射关系，例如 <constructor>所有子元素
     */
    private List<ResultMapping> constructorResultMappings;
    /**
     * 记录了映射关系中不带有 Constructor 标志的映射关系
     */
    private List<ResultMapping> propertyResultMappings;
    /**
     * 记录所有映射关系中涉及的 column属性的集合
     */
    private Set<String> mappedColumns;

    private Set<String> mappedProperties;
    /**
     * 鉴别器，对应 ＜ discriminator ＞节点
     */
    private Discriminator discriminator;
    /**
     * 是否含有嵌套的结果映射，如果某个映射关系中存在 resultMap 属性，且不存在 resultSet 属性，则为 true
     */
    private boolean hasNestedResultMaps;
    /**
     * 是否含有嵌套查询，如果某个属性映射存在 select 属性，则为 true
     */
    private boolean hasNestedQueries;
    /**
     * 是否开启自动映射
     */
    private Boolean autoMapping;

    private ResultMap() {
    }

    /**
     * 建造者模式
     * 用于创建ResultMap
     */
    public static class Builder {
        private static final Log log = LogFactory.getLog(Builder.class);

        private ResultMap resultMap = new ResultMap();

        public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings) {
            this(configuration, id, type, resultMappings, null);
        }

        public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings, Boolean autoMapping) {
            resultMap.configuration = configuration;
            resultMap.id = id;
            resultMap.type = type;
            resultMap.resultMappings = resultMappings;
            resultMap.autoMapping = autoMapping;
        }

        public Builder discriminator(Discriminator discriminator) {
            resultMap.discriminator = discriminator;
            return this;
        }

        public Class<?> type() {
            return resultMap.type;
        }

        public ResultMap build() {
            if (resultMap.id == null) {
                throw new IllegalArgumentException("ResultMaps must have an id");
            }
            resultMap.mappedColumns = new HashSet<String>();
            resultMap.mappedProperties = new HashSet<String>();
            resultMap.idResultMappings = new ArrayList<ResultMapping>();
            resultMap.constructorResultMappings = new ArrayList<ResultMapping>();
            resultMap.propertyResultMappings = new ArrayList<ResultMapping>();
            final List<String> constructorArgNames = new ArrayList<String>();
            for (ResultMapping resultMapping : resultMap.resultMappings) {
                // 检测 <association> 或 <collection> 节点是否包含 select 和 resultMap 属性
                resultMap.hasNestedQueries = resultMap.hasNestedQueries || resultMapping.getNestedQueryId() != null;
                resultMap.hasNestedResultMaps = resultMap.hasNestedResultMaps || (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null);
                final String column = resultMapping.getColumn();
                if (column != null) {
                    // 将 column 转换成大写，并添加到 mappedColumns 集合中
                    resultMap.mappedColumns.add(column.toUpperCase(Locale.ENGLISH));
                } else if (resultMapping.isCompositeResult()) {
                    for (ResultMapping compositeResultMapping : resultMapping.getComposites()) {
                        final String compositeColumn = compositeResultMapping.getColumn();
                        if (compositeColumn != null) {
                            resultMap.mappedColumns.add(compositeColumn.toUpperCase(Locale.ENGLISH));
                        }
                    }
                }
                // 添加属性 property 到 mappedProperties 集合中
                final String property = resultMapping.getProperty();
                if (property != null) {
                    resultMap.mappedProperties.add(property);
                }
                // 检测当前 resultMapping 是否包含 CONSTRUCTOR 标志
                if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
                    // 添加 resultMapping 到 constructorResultMappings 中
                    resultMap.constructorResultMappings.add(resultMapping);
                    if (resultMapping.getProperty() != null) {
                        // 添加属性（constructor 节点的 name 属性）到 constructorArgNames 中
                        constructorArgNames.add(resultMapping.getProperty());
                    }
                } else {
                    // 添加 resultMapping 到 propertyResultMappings 中
                    resultMap.propertyResultMappings.add(resultMapping);
                }
                if (resultMapping.getFlags().contains(ResultFlag.ID)) {
                    // 添加 resultMapping 到 idResultMappings 中
                    resultMap.idResultMappings.add(resultMapping);
                }
            }
            if (resultMap.idResultMappings.isEmpty()) {
                resultMap.idResultMappings.addAll(resultMap.resultMappings);
            }
            if (!constructorArgNames.isEmpty()) {
                // 获取构造方法参数列表
                final List<String> actualArgNames = argNamesOfMatchingConstructor(constructorArgNames);
                if (actualArgNames == null) {
                    throw new BuilderException("Error in result map '" + resultMap.id
                            + "'. Failed to find a constructor in '"
                            + resultMap.getType().getName() + "' by arg names " + constructorArgNames
                            + ". There might be more info in debug log.");
                }
                // 对 constructorResultMappings 按照构造方法参数列表的顺序进行排序
                Collections.sort(resultMap.constructorResultMappings, new Comparator<ResultMapping>() {
                    @Override
                    public int compare(ResultMapping o1, ResultMapping o2) {
                        int paramIdx1 = actualArgNames.indexOf(o1.getProperty());
                        int paramIdx2 = actualArgNames.indexOf(o2.getProperty());
                        return paramIdx1 - paramIdx2;
                    }
                });
            }
            // lock down collections
            // 将以下这些集合变为不可修改集合
            resultMap.resultMappings = Collections.unmodifiableList(resultMap.resultMappings);
            resultMap.idResultMappings = Collections.unmodifiableList(resultMap.idResultMappings);
            resultMap.constructorResultMappings = Collections.unmodifiableList(resultMap.constructorResultMappings);
            resultMap.propertyResultMappings = Collections.unmodifiableList(resultMap.propertyResultMappings);
            resultMap.mappedColumns = Collections.unmodifiableSet(resultMap.mappedColumns);
            return resultMap;
        }

        private List<String> argNamesOfMatchingConstructor(List<String> constructorArgNames) {
            Constructor<?>[] constructors = resultMap.type.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (constructorArgNames.size() == paramTypes.length) {
                    List<String> paramNames = getArgNames(constructor);
                    if (constructorArgNames.containsAll(paramNames)
                            && argTypesMatch(constructorArgNames, paramTypes, paramNames)) {
                        return paramNames;
                    }
                }
            }
            return null;
        }

        private boolean argTypesMatch(final List<String> constructorArgNames,
                                      Class<?>[] paramTypes, List<String> paramNames) {
            for (int i = 0; i < constructorArgNames.size(); i++) {
                Class<?> actualType = paramTypes[paramNames.indexOf(constructorArgNames.get(i))];
                Class<?> specifiedType = resultMap.constructorResultMappings.get(i).getJavaType();
                if (!actualType.equals(specifiedType)) {
                    if (log.isDebugEnabled()) {
                        log.debug("While building result map '" + resultMap.id
                                + "', found a constructor with arg names " + constructorArgNames
                                + ", but the type of '" + constructorArgNames.get(i)
                                + "' did not match. Specified: [" + specifiedType.getName() + "] Declared: ["
                                + actualType.getName() + "]");
                    }
                    return false;
                }
            }
            return true;
        }

        private List<String> getArgNames(Constructor<?> constructor) {
            if (resultMap.configuration.isUseActualParamName() && Jdk.parameterExists) {
                return ParamNameUtil.getParamNames(constructor);
            } else {
                List<String> paramNames = new ArrayList<String>();
                final Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
                int paramCount = paramAnnotations.length;
                for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
                    String name = null;
                    for (Annotation annotation : paramAnnotations[paramIndex]) {
                        if (annotation instanceof Param) {
                            name = ((Param) annotation).value();
                            break;
                        }
                    }
                    paramNames.add(name != null ? name : "arg" + paramIndex);
                }
                return paramNames;
            }
        }
    }

    public String getId() {
        return id;
    }

    public boolean hasNestedResultMaps() {
        return hasNestedResultMaps;
    }

    public boolean hasNestedQueries() {
        return hasNestedQueries;
    }

    public Class<?> getType() {
        return type;
    }

    public List<ResultMapping> getResultMappings() {
        return resultMappings;
    }

    public List<ResultMapping> getConstructorResultMappings() {
        return constructorResultMappings;
    }

    public List<ResultMapping> getPropertyResultMappings() {
        return propertyResultMappings;
    }

    public List<ResultMapping> getIdResultMappings() {
        return idResultMappings;
    }

    public Set<String> getMappedColumns() {
        return mappedColumns;
    }

    public Set<String> getMappedProperties() {
        return mappedProperties;
    }

    public Discriminator getDiscriminator() {
        return discriminator;
    }

    public void forceNestedResultMaps() {
        hasNestedResultMaps = true;
    }

    public Boolean getAutoMapping() {
        return autoMapping;
    }

}
