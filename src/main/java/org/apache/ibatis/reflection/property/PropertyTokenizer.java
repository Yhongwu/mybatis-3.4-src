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

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
    private String name;
    private String indexedName;
    private String index;
    private String children;

    /**
     * 解析属性 通过，和[] 进行解析
     * 继承了Iterator 可迭代 见next()
     * 比如class[0].student[0].name  班级->学生->姓名
     * 第一次迭代：indexedName = class[0],name = class,index = 0, children = student[0].name
     * children参与下次迭代进行解析
     * @param fullname
     */
    public PropertyTokenizer(String fullname) {
        // 检测传入的参数中是否包含字符 '.'
        int delim = fullname.indexOf('.');
        // 以点位为界，进行分割。比如：
        // 比如class.student.name,则name=class,children=student.name
        if (delim > -1) {
            name = fullname.substring(0, delim);
            children = fullname.substring(delim + 1);
        } else {
            // fullname不存在.
            name = fullname;
            children = null;
        }
        indexedName = name;
        // 检测传入的参数中是否包含字符 '['
        delim = name.indexOf('[');
        // 获取中括号[]里的内容,如果是数组或list，则内容为下标，如果是Map，则[] 中的内容为键，
        if (delim > -1) {
            index = name.substring(delim + 1, name.length() - 1);
            // 获取分解符前面的内容，比如 fullname = articles[1]，name = articles
            name = name.substring(0, delim);
            //class[0].student[0].name: indexedName = class[0],name = class,index = 0, children = student[0].name
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    @Override
    public PropertyTokenizer next() {
        // 对 children 进行再次切分，用于解析多重复合属性
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }
}
