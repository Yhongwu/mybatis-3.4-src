package my.test01;

import javassist.tools.reflect.Metaobject;
import my.test01.util.Permission;
import my.test01.util.Role;
import my.test01.util.User;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.submitted.result_handler_type.ObjectFactory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Howard Yao on 2018/7/28.
 */
public class Test01 {
    @Test
    public void test_1() {
        //Role r1 = new Role(1,"admin");
        //Role r2 = new Role(2,"customer");
        //List<Role> roles = new ArrayList<>();
        //Role rr = roles[0];
        //roles.add(r1);
        //roles.add(r2);
        //User u = new User("tom",roles);
        MetaClass metaClass = MetaClass.forClass(User.class, new DefaultReflectorFactory());

        String property = metaClass.findProperty("roles.code"); // roles.
        System.out.println(property);
        System.out.println(metaClass.findProperty("roles[0].code"));// roles.
        //解析user中的 roles[0].code
        // 通过role[0]从user类获取是否有role，拼接，继续判断user中是否有code，有则继续拼接？？？
    }

    /**
     * MetaClass:类级别的元信息的获取和封装
     * MetaClass测试：
     * hasGetter 和 getGetterType
     */
    @Test
    public void test_2() {
        //Role r1 = new Role(1,"admin");
        //Role r2 = new Role(2,"customer");
        //List<Role> roles = new ArrayList<>();
        //Role rr = roles[0];
        //roles.add(r1);
        //roles.add(r2);
        //User u = new User("tom",roles);

        // 类级别 无需实例化
        MetaClass metaClass = MetaClass.forClass(User.class, new DefaultReflectorFactory());
        // 判断user中是否有role role中是否有code属性
        System.out.println(metaClass.hasGetter("roles[0].code"));// true
        System.out.println(metaClass.hasGetter("roles[0].p.desc"));
        // 不一定要有getter方法
        System.out.println(metaClass.getGetterType("roles")); //interface java.util.List
        System.out.println(metaClass.getGetterType("roles[0]")); //class my.test01.util.Role
        System.out.println(metaClass.getGetterType("roles[0].code")); //class java.lang.Integer
        System.out.println(metaClass.getGetterType("roles[0].tt")); //class java.lang.Double
        System.out.println(metaClass.getGetterType("roles[0].p.desc")); //class java.lang.String
    }

    /**
     * MetaObject类的测试：getValue
     * 设计org.apache.ibatis.reflection.wrapper包下的几个类 对象级别的元信息的获取和封装
     * 部分方法效果同MetaClass中的方法 底层调用的就是MetaClass的方法
     */
    @Test
    public void test_3() {

        Role r2 = new Role(2,"customer");
        Permission  p = new Permission("查询");
        Role r1 = new Role(1,"admin",p);
        List<Role> roles = new ArrayList<>();

        roles.add(r1);
        roles.add(r2);
        User u = new User("tom",roles);

        // 传入实例化的对象
        MetaObject metaobject = MetaObject.forObject(u,new ObjectFactory(),new DefaultObjectWrapperFactory(),new DefaultReflectorFactory());
        // 获取u中的role的list的第一个对象的code属性值
        // 经过多次递归
        System.out.println(metaobject.getValue("roles[0].code")); // 1
        // role中无明确的setter和getter p的方法 可以获取
        System.out.println(metaobject.getValue("roles[0].p.desc")); //查询

        System.out.println(metaobject.getGetterType("roles")); //interface java.util.List
        // 给u的role[0]的code属性设值
        metaobject.setValue("roles[0].code",10);
        System.out.println(metaobject.getValue("roles[0].code")); // 10
        System.out.println(metaobject.getGetterType("roles[0].code")); //class java.lang.Integer
        System.out.println(metaobject.getGetterType("roles[0].tt")); //class java.lang.Double
        System.out.println(metaobject.getGetterType("roles[0].p.desc"));//class java.lang.String
    }
}
