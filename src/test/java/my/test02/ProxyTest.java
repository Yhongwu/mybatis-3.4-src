package my.test02;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * 动态代理测试
 */
public class ProxyTest {
    public static void main(String[] args) {
        InvocationHandler handler = new DynamicProxy(new RealSubJect());
        SubJect subJect =(SubJect) Proxy.newProxyInstance(handler.getClass().getClassLoader(),RealSubJect.class.getInterfaces(),handler);
        subJect.method1();
        subJect.method2();

    }
}
