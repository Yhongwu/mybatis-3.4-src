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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 *
 * 实现一个mybatis插件需要实现的接口，并添加@Intercepts和@Signature注解，用于指定想要拦截的目标方法
 * 允许拦截的方法：
 *     Executor: update 方法，query 方法，flushStatements 方法，commit 方法，rollback 方法， getTransaction 方法，close 方法，isClosed 方法
 *     ParameterHandler: getParameterObject 方法，setParameters 方法
 *     ResultSetHandler: handleResultSets 方法，handleOutputParameters 方法
 *     StatementHandler: prepare 方法，parameterize 方法，batch 方法，update 方法，query 方法
 *
 *  比如:
 *  @Intercepts(value = {@Signature(
 *         type= Executor.class,                              //这里对应上面4个类
 *         method = "update",                                 //这里对应4个类里面的参数
 *         args = {MappedStatement.class,Object.class})})     //这里的参数类型，是对应4个类中的各种方法的参数。如果方法没有参数，这里直接写{}就可以了
 */
public interface Interceptor {

    Object intercept(Invocation invocation) throws Throwable;

    Object plugin(Object target);

    void setProperties(Properties properties);

}
