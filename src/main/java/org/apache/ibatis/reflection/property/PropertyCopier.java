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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

/**
 * @author Clinton Begin
 */
public final class PropertyCopier {

    private PropertyCopier() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 实现相同类型的两个对象之间的属性值拷贝
     * @param type
     * @param sourceBean
     * @param destinationBean
     */
    public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
        Class<?> parent = type;
        while (parent != null) {
            final Field[] fields = parent.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    //方法:get(Object obj) 返回指定对象obj上此 Field 表示的字段的值
                    //方法:set(Object obj, Object value)  将指定对象变量上此 Field 对象表示的字段设置为指定的新值
                    field.set(destinationBean, field.get(sourceBean));
                } catch (Exception e) {
                    // Nothing useful to do, will only fail on final fields, which will be ignored.
                }
            }
            //继续拷贝父类中定义的字段
            parent = parent.getSuperclass();
        }
    }

}
