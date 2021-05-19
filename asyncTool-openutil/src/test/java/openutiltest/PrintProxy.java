package openutiltest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 便于测试的jdk动态代理
 *
 * @author create by TcSnZh on 2021/5/16-下午11:38
 */
public class PrintProxy<I> {
    public PrintProxy(Class<I> clazz) {
        this.interfaceClazz = clazz;
    }

    private final Class<?> interfaceClazz;

    public I proxyTo(I obj, String objNickName) {
        //noinspection unchecked
        return (I) Proxy.newProxyInstance(
                obj.getClass().getClassLoader(),
                new Class[]{interfaceClazz},
                (proxy, method, args) -> {
                    String methodInfo = methodInfo(method);
                    try {
                        Object res = method.invoke(obj, args);
                        System.out.printf(objNickName + " 执行方法:  %-40s  -->  方法返回值:  %-20s  --> this.toString() =  %-40s\n",
                                methodInfo, res, obj);
                        return res;
                    } catch (Exception e) {
                        System.err.printf(objNickName + " 执行方法:  %-40s  -->  异常信息:  %-40s  --> this.toString() =  %-40s\n",
                                methodInfo, e.getClass().getSimpleName() + " : " + e.getMessage(), obj
                        );
                        throw e;
                    }
                }
        );
    }

    private static String methodInfo(Method method) {
        StringBuilder sb = new StringBuilder().append(method.getName()).append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            sb.append(parameterType.getSimpleName()).append(", ");
        }
        if (method.getParameterTypes().length > 0) {
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(')').toString();
    }

}
