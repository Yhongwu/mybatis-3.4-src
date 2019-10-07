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

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * 完成方法名到属性名的转换，以及多种检测操作
 * @author Clinton Begin
 */
public final class PropertyNamer {

    // 工具类 私有构造方法
    private PropertyNamer() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 方法名转换成属性名
     * 将方法名开头的"is"、"get"或"set"截掉，并将首字母小写
     * @param name
     * @return
     */
    public static String methodToProperty(String name) {
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get") || name.startsWith("set")) {
            name = name.substring(3);
        } else {
            throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
        }

        if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) {
            name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }

        return name;
    }

    /**
     * 判断是否以”is”、”get”或”set”开头
     * @param name
     * @return
     */
    public static boolean isProperty(String name) {
        return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
    }

    /**
     * 判断是否getter方法
     * @param name
     * @return
     */
    public static boolean isGetter(String name) {
        return name.startsWith("get") || name.startsWith("is");
    }

    /**
     * 判断是否setter方法
     * @param name
     * @return
     */
    public static boolean isSetter(String name) {
        return name.startsWith("set");
    }

}
