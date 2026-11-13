package dev.muuko.honorfreeclip2;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Select only Android lifecycle signatures. Never guess an obfuscated initializer. */
final class LifecycleHookResolver {
    private LifecycleHookResolver() { }
    static List<Method> resumeMethods(Class<?> host, Class<?> boundary) {
        List<Method> result = new ArrayList<>();
        if (host == null || boundary == null || !boundary.isAssignableFrom(host)) return result;
        for (String name : new String[]{"onResume", "onPostResume"}) {
            for (Class<?> current = host; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    int flags = method.getModifiers();
                    if (method.getReturnType() == void.class && !Modifier.isStatic(flags)
                            && !Modifier.isAbstract(flags) && !Modifier.isPrivate(flags)) {
                        result.add(method);
                        break;
                    }
                } catch (NoSuchMethodException ignored) { }
                if (current == boundary) break;
            }
        }
        return result;
    }
}
