package dev.muuko.honorfreeclip2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Reflection {
    private Reflection() {}

    static Object field(Object instance, String name) throws ReflectiveOperationException {
        if (instance == null) return null;
        for (Class<?> cls = instance.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static Object call(Object instance, String name, Class<?>[] parameters, Object... args)
            throws ReflectiveOperationException {
        if (instance == null) return null;
        for (Class<?> cls = instance.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Method method = cls.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method.invoke(instance, args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    static Object callNoArgs(Object instance, String name) throws ReflectiveOperationException {
        return call(instance, name, new Class<?>[0]);
    }
}
